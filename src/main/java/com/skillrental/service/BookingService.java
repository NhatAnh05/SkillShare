package com.skillrental.service;

import com.skillrental.dto.BookingForm;
import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service @RequiredArgsConstructor public class BookingService {
    private final ProviderProfileRepository providers;
    private final ServiceCatalogService services;
    private final ProviderEligibilityService providerEligibility;
    private final BookingRepository bookings;
    private final RentalOrderRepository orders;
    private final CartItemRepository cartItems;
    private final RentalOrderItemRepository orderItems;
    private final RentalOrderStatusHistoryRepository orderHistory;
    private final EscrowService escrow;
    private final ServicePriceCalculator priceCalculator;
    @Transactional public RentalOrder create(User customer, Long providerId, BookingForm f) {
        if (customer == null || customer.getRole() != Role.CUSTOMER) {
            throw new BookingUnavailableException("Chỉ Customer mới được tạo booking.");
        }
        if (f.getIdempotencyKey() == null || f.getIdempotencyKey().isBlank()
                || f.getIdempotencyKey().length() > 36) {
            throw new BookingUnavailableException("Mã yêu cầu đặt dịch vụ không hợp lệ. Hãy tải lại biểu mẫu.");
        }
        Optional<Booking> previousBooking = bookings.findByIdempotencyKey(f.getIdempotencyKey());
        if (previousBooking.isPresent()) {
            return existingOrderForReplay(previousBooking.get(), customer, providerId, f);
        }
        CartItem cartItem = f.getCartItemId() == null ? null
                : validateCartItemForCheckout(f.getCartItemId(), customer, f.getServiceId(), f.getQuantity());

        var p=providers.findById(providerId)
                .orElseThrow(() -> new BookingUnavailableException("Không tìm thấy Provider."));
        if (!providerEligibility.isEligible(p)) {
            throw new BookingUnavailableException("Provider hiện chưa được duyệt hoặc không hoạt động.");
        }
        ServiceCatalog service = services.requireAvailablePublishedService(f.getServiceId(), p);
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            throw new IllegalArgumentException("Khung giờ đã qua");
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10,0), java.time.LocalTime.of(12,0),
                java.time.LocalTime.of(18,0), java.time.LocalTime.of(20,0)).contains(f.getTimeSlot())) {
            throw new IllegalArgumentException("Khung giờ không hợp lệ");
        }
        if (cartItem != null
                && cartItem.getUnitPriceSnapshot().compareTo(service.getPrice()) != 0
                && !f.isConfirmPriceChange()) {
            throw new PriceChangedException(
                    "Giá dịch vụ đã thay đổi thành " + service.getPrice().toPlainString()
                            + "đ. Vui lòng xác nhận giá mới trước khi tiếp tục.");
        }
        int quantity = cartItem == null ? f.getQuantity() : cartItem.getQuantity();
        ServicePriceCalculator.PriceBreakdown price =
                priceCalculator.calculate(service, quantity, f.getDurationHours(), f.isPremium());
        var b=bookings.saveAndFlush(Booking.builder().customer(customer).provider(p.getUser()).service(service)
                .serviceName(service.getName()).bookingDate(f.getBookingDate()).timeSlot(f.getTimeSlot())
                .note(f.getNote()).premium(f.isPremium()).directBooking(f.isDirectBooking())
                .durationHours(f.getDurationHours()).amount(price.total()).status(BookingStatus.PENDING)
                .idempotencyKey(f.getIdempotencyKey()).build());
        RentalOrder order = orders.saveAndFlush(RentalOrder.builder().booking(b).customer(customer).provider(p.getUser())
                .subtotal(price.total()).discount(BigDecimal.ZERO).total(price.total())
                .status(OrderStatus.PENDING_PROVIDER_ACCEPTANCE).createdAt(LocalDateTime.now()).build());
        orderItems.saveAndFlush(RentalOrderItem.builder().order(order).service(service).provider(p.getUser())
                .booking(b).serviceNameSnapshot(service.getName()).quantity(quantity)
                .unitPrice(price.unitPrice()).lineTotal(price.total()).build());
        orderHistory.save(RentalOrderStatusHistory.builder().order(order).previousStatus(null)
                .newStatus(OrderStatus.PENDING_PROVIDER_ACCEPTANCE.name()).changedBy(customer)
                .note("Customer tạo booking cho dịch vụ " + service.getName() + ".").build());
        escrow.reserveForBooking(order);
        if (cartItem != null) {
            cartItem.getCart().setUpdatedAt(LocalDateTime.now());
            cartItems.delete(cartItem);
            cartItems.flush();
        }
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<RentalOrder> findOrderForIdempotentReplay(String key, User customer, Long providerId,
                                                               BookingForm form) {
        return bookings.findByIdempotencyKey(key)
                .map(booking -> existingOrderForReplay(booking, customer, providerId, form));
    }

    private RentalOrder existingOrderForReplay(Booking booking, User customer, Long providerId, BookingForm form) {
        boolean matches = booking.getCustomer().getId().equals(customer.getId())
                && booking.getProvider().getId().equals(providers.findById(providerId)
                        .map(provider -> provider.getUser().getId()).orElse(null))
                && booking.getService() != null
                && booking.getService().getId().equals(form.getServiceId())
                && Objects.equals(booking.getBookingDate(), form.getBookingDate())
                && Objects.equals(booking.getTimeSlot(), form.getTimeSlot())
                && Objects.equals(booking.getNote(), form.getNote())
                && Objects.equals(booking.getPremium(), form.isPremium())
                && Objects.equals(booking.getDirectBooking(), form.isDirectBooking())
                && Objects.equals(booking.getDurationHours(), form.getDurationHours())
                && orderItems.findByBookingId(booking.getId()).stream().findFirst()
                .map(item -> {
                    if (!Objects.equals(item.getQuantity(), form.getQuantity())
                            || item.getService() == null || item.getUnitPrice() == null) {
                        return false;
                    }
                    BigDecimal historicalTotal = priceCalculator.calculate(item.getUnitPrice(),
                            item.getService().getPricingType(), item.getQuantity(),
                            booking.getDurationHours(), booking.getPremium()).total();
                    return historicalTotal.compareTo(booking.getAmount()) == 0
                            && Objects.equals(item.getLineTotal(), booking.getAmount());
                })
                .orElse(false);
        if (!matches) {
            throw new BookingUnavailableException("Mã yêu cầu đã được sử dụng cho thông tin đặt dịch vụ khác.");
        }
        return orders.findByBooking(booking)
                .orElseThrow(() -> new BookingUnavailableException("Không tìm thấy đơn hàng của yêu cầu đã xử lý."));
    }

    private CartItem validateCartItemForCheckout(Long cartItemId, User customer, Long serviceId, int requestedQuantity) {
        CartItem item = cartItems.findByIdForUpdate(cartItemId)
                .orElseThrow(() -> new BookingUnavailableException("Không tìm thấy dịch vụ trong giỏ hàng."));
        Cart cart = item.getCart();
        if (cart.getStatus() != CartStatus.ACTIVE || cart.getCustomer() == null
                || !cart.getCustomer().getId().equals(customer.getId())) {
            throw new BookingUnavailableException("Giỏ hàng không thuộc Customer hiện tại hoặc không còn hoạt động.");
        }
        if (requestedQuantity != item.getQuantity()) {
            throw new BookingUnavailableException(
                    "Số lượng trong giỏ đã thay đổi. Hãy tải lại trang đặt lịch để xác nhận số lượng hiện tại.");
        }
        if (!item.getService().getId().equals(serviceId)) {
            throw new BookingUnavailableException("Dịch vụ chọn đặt không khớp với dòng trong giỏ hàng.");
        }
        return item;
    }

    public static class BookingUnavailableException extends RuntimeException {
        public BookingUnavailableException(String message) {
            super(message);
        }
    }

    public static final class PriceChangedException extends BookingUnavailableException {
        public PriceChangedException(String message) {
            super(message);
        }
    }
}
