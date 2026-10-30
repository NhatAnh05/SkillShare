package com.skillrental.controller;

import com.skillrental.dto.BookingForm;
import com.skillrental.entity.*;
import com.skillrental.repository.CartItemRepository;
import com.skillrental.repository.CartRepository;
import com.skillrental.service.*;
import com.skillrental.service.BookingService.BookingUnavailableException;
import com.skillrental.service.BookingService.PriceChangedException;
import com.skillrental.service.ServiceCatalogService.ServiceUnavailableException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor public class BookingController {
    private static final Logger log = LoggerFactory.getLogger(BookingController.class);
    private final MarketplaceService marketplace;
    private final CurrentUserService current;
    private final BookingService bookings;
    private final ServiceCatalogService services;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ServicePriceCalculator priceCalculator;
    @GetMapping("/booking/provider/{id}") String form(@PathVariable("id") Long id,
            @RequestParam(defaultValue="false") boolean premium,
            @RequestParam(required=false) Long serviceId,
            @RequestParam(required=false) Long cartItemId,
            Authentication authentication, Model m) {
        var user = current.require(authentication);
        if (user.getRole() != Role.CUSTOMER) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Chỉ Customer mới được đặt Provider.");
        var f=new BookingForm();
        f.setPremium(premium);
        f.setDirectBooking(true);
        f.setIdempotencyKey(java.util.UUID.randomUUID().toString());
        var provider = marketplace.get(id);
        if (!services.providerIsEligible(provider.getUser())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Provider hiện chưa được duyệt hoặc không hoạt động.");
        }
        var availableServices = services.availablePublishedServices(provider.getUser());
        if (cartItemId != null) {
            Cart cart = carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(user, CartStatus.ACTIVE)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng hiện tại."));
            CartItem item = cartItems.findByIdAndCart(cartItemId, cart)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Dịch vụ không nằm trong giỏ hàng hiện tại."));
            f.setServiceId(item.getService().getId());
            f.setQuantity(item.getQuantity());
            if (!item.getService().getProvider().getId().equals(provider.getUser().getId())) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dịch vụ không thuộc Provider đã chọn.");
            }
            m.addAttribute("cartItem", item);
            m.addAttribute("priceChanged",
                    item.getUnitPriceSnapshot().compareTo(item.getService().getPrice()) != 0);
        }
        if (availableServices.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Provider hiện không có dịch vụ đã duyệt để đặt.");
        }
        if (f.getServiceId() != null
                && availableServices.stream().noneMatch(service -> service.getId().equals(f.getServiceId()))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Dịch vụ không thuộc Provider hoặc hiện không được mở bán.");
        }
        f.setCartItemId(cartItemId);
        m.addAttribute("form",f);
        m.addAttribute("provider",provider);
        m.addAttribute("availableServices",availableServices);
        addPriceModel(m, f, availableServices);
        return "booking/booking";
    }
    @PostMapping("/booking/provider/{id}") String submit(@PathVariable("id") Long id,
            @Valid @ModelAttribute("form") BookingForm f, BindingResult br, Authentication a,
            Model m, RedirectAttributes ra) {
        var user = current.require(a);
        if (user.getRole() != Role.CUSTOMER) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ Customer mới được đặt Provider.");
        }
        if(br.hasErrors()) {
            populateBookingModel(id, f, user, m);
            return "booking/booking";
        }
        if (f.getBookingDate().equals(java.time.LocalDate.now()) &&
            f.getTimeSlot().isBefore(java.time.LocalTime.now())) {
            br.rejectValue("timeSlot", "past", "Khung giờ đã qua");
            populateBookingModel(id, f, user, m);
            return "booking/booking";
        }
        if (!java.util.Set.of(java.time.LocalTime.of(10, 0), java.time.LocalTime.of(12, 0),
                              java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0))
                .contains(f.getTimeSlot())) {
            br.rejectValue("timeSlot", "invalid", "Khung giờ không hợp lệ");
            populateBookingModel(id, f, user, m);
            return "booking/booking";
        }
        try {
            var o=bookings.create(user,id,f);
            ra.addFlashAttribute("success", "Đã gửi yêu cầu đặt dịch vụ. Toàn bộ chi phí đã được giữ trong ví Escrow; Provider cần xác nhận nhận việc.");
            return "redirect:/orders/"+o.getId()+"/progress";
        } catch (PriceChangedException e) {
            populateBookingModel(id, f, user, m);
            m.addAttribute("bookingError", e.getMessage());
            return "booking/booking";
        } catch (BookingUnavailableException | ServiceUnavailableException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/marketplace";
        } catch (DataIntegrityViolationException e) {
            try {
                var existing = bookings.findOrderForIdempotentReplay(f.getIdempotencyKey(), user, id, f);
                if (existing.isPresent()) {
                    ra.addFlashAttribute("info", "Yêu cầu này đã được xử lý trước đó; đã mở đơn hiện có.");
                    return "redirect:/orders/" + existing.get().getId() + "/progress";
                }
            } catch (BookingUnavailableException ignored) {
                log.warn("Idempotency key conflict with a different booking payload.");
            }
            log.error("Không thể lưu booking/order do vi phạm ràng buộc dữ liệu providerId={}", id, e);
            ra.addFlashAttribute("error", "Không thể lưu yêu cầu đặt dịch vụ. Vui lòng kiểm tra dữ liệu và thử lại.");
            return "redirect:/marketplace";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/wallet";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/booking/provider/" + id
                    + (f.getServiceId() == null ? "" : "?serviceId=" + f.getServiceId())
                    + (f.getCartItemId() == null ? "" : (f.getServiceId() == null ? "?" : "&") + "cartItemId=" + f.getCartItemId());
        }
    }

    private void populateBookingModel(Long providerId, BookingForm form,
                                      com.skillrental.entity.User customer, Model model) {
        var provider = marketplace.get(providerId);
        model.addAttribute("provider", provider);
        var availableServices = services.availablePublishedServices(provider.getUser());
        model.addAttribute("availableServices", availableServices);
        if (form.getCartItemId() != null) {
            carts.findFirstByCustomerAndStatusOrderByUpdatedAtDesc(customer, CartStatus.ACTIVE)
                    .flatMap(cart -> cartItems.findByIdAndCart(form.getCartItemId(), cart))
                    .ifPresent(item -> {
                        model.addAttribute("cartItem", item);
                        model.addAttribute("priceChanged",
                                item.getUnitPriceSnapshot().compareTo(item.getService().getPrice()) != 0);
                    });
        }
        model.addAttribute("form", form);
        addPriceModel(model, form, availableServices);
    }

    private void addPriceModel(Model model, BookingForm form,
                               java.util.List<com.skillrental.entity.ServiceCatalog> availableServices) {
        model.addAttribute("defaultDurationHours", ServicePriceCalculator.DEFAULT_DURATION_HOURS);
        var selectedService = availableServices.stream()
            .filter(service -> service.getId().equals(form.getServiceId()))
            .findFirst().orElse(null);
        model.addAttribute("selectedService", selectedService);
        if (selectedService != null) {
            int quantity = Math.max(1, Math.min(99, form.getQuantity()));
            int hours = Math.max(1, Math.min(24, form.getDurationHours()));
            model.addAttribute("priceBreakdown",
                    priceCalculator.calculate(selectedService, quantity, hours, form.isPremium()));
        } else {
            model.addAttribute("priceBreakdown", null);
        }
    }
}
