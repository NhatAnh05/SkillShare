package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.exception.InvalidOrderTransitionException;
import com.skillrental.exception.OrderAccessDeniedException;
import com.skillrental.repository.BookingRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.RentalOrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test thuần (không khởi động Spring, không cần database).
 * Repository được giả lập bằng java.lang.reflect.Proxy: chỉ ghi lại các lần gọi save().
 */
class OrderStatusServiceTest {

    private final List<Object> savedOrders = new ArrayList<>();
    private final List<Object> savedBookings = new ArrayList<>();
    private final List<Object> savedHistory = new ArrayList<>();

    private OrderStatusService service;
    private User customer, otherCustomer, provider, otherProvider, admin;

    @BeforeEach
    void setUp() {
        service = new OrderStatusService(
                recorder(RentalOrderRepository.class, savedOrders),
                recorder(BookingRepository.class, savedBookings),
                recorder(RentalOrderStatusHistoryRepository.class, savedHistory));
        customer = user(1L, Role.CUSTOMER);
        otherCustomer = user(2L, Role.CUSTOMER);
        provider = user(3L, Role.PROVIDER);
        otherProvider = user(4L, Role.PROVIDER);
        admin = user(5L, Role.ADMIN);
    }

    // ---------- bảng chuyển trạng thái ----------

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, PAID,       CONFIRMED",
            "PENDING_PAYMENT, CANCELLED,  CANCELLED",
            "PAID,            PROCESSING, IN_PROGRESS",
            "PAID,            CANCELLED,  CANCELLED",
            "PROCESSING,      COMPLETED,  COMPLETED",
            "PROCESSING,      CANCELLED,  CANCELLED"
    })
    void validTransitionUpdatesOrderBookingAndHistory(OrderStatus from, OrderStatus to, BookingStatus expectedBooking) {
        RentalOrder order = order(from);

        service.transition(order, to, admin, "ghi chú");

        assertEquals(to, order.getStatus());
        assertEquals(expectedBooking, order.getBooking().getStatus());
        assertEquals(1, savedOrders.size());
        assertEquals(1, savedBookings.size());
        assertEquals(1, savedHistory.size());
        RentalOrderStatusHistory h = (RentalOrderStatusHistory) savedHistory.get(0);
        assertEquals(from.name(), h.getPreviousStatus());
        assertEquals(to.name(), h.getNewStatus());
        assertSame(admin, h.getChangedBy());
        assertEquals("ghi chú", h.getNote());
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, PROCESSING",
            "PENDING_PAYMENT, COMPLETED",
            "PAID,            COMPLETED",
            "PAID,            PAID",
            "PAID,            PENDING_PAYMENT",
            "PROCESSING,      PAID",
            "PROCESSING,      PENDING_PAYMENT",
            "COMPLETED,       CANCELLED",
            "COMPLETED,       PROCESSING",
            "CANCELLED,       PAID",
            "CANCELLED,       PENDING_PAYMENT"
    })
    void invalidTransitionIsRejectedEvenForAdmin(OrderStatus from, OrderStatus to) {
        RentalOrder order = order(from);

        assertThrows(InvalidOrderTransitionException.class, () -> service.transition(order, to, admin, null));

        assertEquals(from, order.getStatus());
        assertNothingSaved();
    }

    @Test
    void finalStatesHaveNoNextStatus() {
        assertTrue(OrderStatusService.nextStatuses(OrderStatus.COMPLETED).isEmpty());
        assertTrue(OrderStatusService.nextStatuses(OrderStatus.CANCELLED).isEmpty());
        assertTrue(OrderStatusService.isAllowed(OrderStatus.PAID, OrderStatus.PROCESSING));
        assertFalse(OrderStatusService.isAllowed(OrderStatus.PAID, OrderStatus.COMPLETED));
    }

    // ---------- phân quyền ----------

    @Test
    void customerCanPayOwnOrderButProviderAndOtherCustomerCannot() {
        assertDenied(order(OrderStatus.PENDING_PAYMENT), OrderStatus.PAID, provider);
        assertDenied(order(OrderStatus.PENDING_PAYMENT), OrderStatus.PAID, otherCustomer);

        RentalOrder order = order(OrderStatus.PENDING_PAYMENT);
        service.transition(order, OrderStatus.PAID, customer, null);
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void onlyOwningProviderCanStartProcessing() {
        assertDenied(order(OrderStatus.PAID), OrderStatus.PROCESSING, customer);
        assertDenied(order(OrderStatus.PAID), OrderStatus.PROCESSING, otherProvider);

        RentalOrder order = order(OrderStatus.PAID);
        service.transition(order, OrderStatus.PROCESSING, provider, null);
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals(BookingStatus.IN_PROGRESS, order.getBooking().getStatus());
    }

    @Test
    void providerAndCustomerCanCompleteButStrangersCannot() {
        assertDenied(order(OrderStatus.PROCESSING), OrderStatus.COMPLETED, otherProvider);
        assertDenied(order(OrderStatus.PROCESSING), OrderStatus.COMPLETED, otherCustomer);

        RentalOrder byProvider = order(OrderStatus.PROCESSING);
        service.transition(byProvider, OrderStatus.COMPLETED, provider, null);
        assertEquals(OrderStatus.COMPLETED, byProvider.getStatus());
        assertNotNull(byProvider.getCompletedAt());

        RentalOrder byCustomer = order(OrderStatus.PROCESSING);
        service.transition(byCustomer, OrderStatus.COMPLETED, customer, null);
        assertEquals(OrderStatus.COMPLETED, byCustomer.getStatus());
    }

    @Test
    void cancelPermissionsFollowOrderStatus() {
        // khách: được hủy khi chờ thanh toán hoặc đã thanh toán, không được hủy khi đang thực hiện
        service.transition(order(OrderStatus.PENDING_PAYMENT), OrderStatus.CANCELLED, customer, "đổi ý");
        service.transition(order(OrderStatus.PAID), OrderStatus.CANCELLED, customer, "đổi ý");
        assertDenied(order(OrderStatus.PROCESSING), OrderStatus.CANCELLED, customer);
        assertDenied(order(OrderStatus.PENDING_PAYMENT), OrderStatus.CANCELLED, otherCustomer);

        // provider: chỉ từ chối được đơn đã thanh toán của mình
        service.transition(order(OrderStatus.PAID), OrderStatus.CANCELLED, provider, "bận");
        assertDenied(order(OrderStatus.PENDING_PAYMENT), OrderStatus.CANCELLED, provider);
        assertDenied(order(OrderStatus.PROCESSING), OrderStatus.CANCELLED, provider);
        assertDenied(order(OrderStatus.PAID), OrderStatus.CANCELLED, otherProvider);

        // admin: hủy được đơn đang thực hiện
        service.transition(order(OrderStatus.PROCESSING), OrderStatus.CANCELLED, admin, "can thiệp");
    }

    @Test
    void missingActorIsDenied() {
        assertDenied(order(OrderStatus.PAID), OrderStatus.PROCESSING, null);
    }

    // ---------- dữ liệu ghi kèm ----------

    @Test
    void cancelStoresReasonActorAndTimeOnOrderAndBooking() {
        RentalOrder order = order(OrderStatus.PAID);

        service.transition(order, OrderStatus.CANCELLED, customer, "Bận việc đột xuất");

        assertEquals("Bận việc đột xuất", order.getCancellationReason());
        assertSame(customer, order.getCancelledBy());
        assertNotNull(order.getCancelledAt());
        Booking booking = order.getBooking();
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("Bận việc đột xuất", booking.getCancellationReason());
        assertSame(customer, booking.getCancelledBy());
        assertNotNull(booking.getCancelledAt());
        assertEquals("Bận việc đột xuất", ((RentalOrderStatusHistory) savedHistory.get(0)).getNote());
    }

    @Test
    void orderWithoutBookingStillWorks() {
        RentalOrder order = order(OrderStatus.PAID);
        order.setBooking(null);

        service.transition(order, OrderStatus.PROCESSING, provider, null);

        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertTrue(savedBookings.isEmpty());
        assertEquals(1, savedHistory.size());
    }

    // ---------- tiện ích ----------

    private void assertDenied(RentalOrder order, OrderStatus to, User actor) {
        OrderStatus before = order.getStatus();
        int ordersBefore = savedOrders.size();
        assertThrows(OrderAccessDeniedException.class, () -> service.transition(order, to, actor, null));
        assertEquals(before, order.getStatus(), "Trạng thái không được đổi khi bị từ chối");
        assertEquals(ordersBefore, savedOrders.size(), "Không được save khi bị từ chối");
    }

    private void assertNothingSaved() {
        assertTrue(savedOrders.isEmpty());
        assertTrue(savedBookings.isEmpty());
        assertTrue(savedHistory.isEmpty());
    }

    private RentalOrder order(OrderStatus status) {
        Booking booking = Booking.builder().id(100L).status(bookingStatusOf(status)).build();
        return RentalOrder.builder().id(10L).customer(customer).provider(provider)
                .booking(booking).status(status).build();
    }

    private static BookingStatus bookingStatusOf(OrderStatus s) {
        return switch (s) {
            case PENDING_PAYMENT -> BookingStatus.PENDING;
            case PAID -> BookingStatus.CONFIRMED;
            case PROCESSING -> BookingStatus.IN_PROGRESS;
            case COMPLETED -> BookingStatus.COMPLETED;
            case CANCELLED -> BookingStatus.CANCELLED;
        };
    }

    private static User user(Long id, Role role) {
        return User.builder().id(id).role(role).fullName("user" + id).email("u" + id + "@test.vn").password("x").build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T recorder(Class<T> repository, List<Object> sink) {
        return (T) Proxy.newProxyInstance(repository.getClassLoader(), new Class<?>[]{repository}, (proxy, method, args) -> {
            if (method.getName().equals("save")) {
                sink.add(args[0]);
                return args[0];
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.getName().equals("toString") ? repository.getSimpleName() : null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
