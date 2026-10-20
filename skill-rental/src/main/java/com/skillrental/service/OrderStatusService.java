package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.exception.InvalidOrderTransitionException;
import com.skillrental.exception.OrderAccessDeniedException;
import com.skillrental.repository.BookingRepository;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.RentalOrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Nơi DUY NHẤT được phép đổi trạng thái đơn hàng.
 * Mỗi lần đổi: kiểm tra bảng chuyển hợp lệ → kiểm tra quyền → cập nhật đơn,
 * đồng bộ booking → ghi lịch sử (rental_order_status_history).
 */
@Service
@RequiredArgsConstructor
public class OrderStatusService {

    /** Bảng chuyển trạng thái hợp lệ (xem README, mục "Trạng thái đơn hàng"). */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PAID, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final RentalOrderRepository orders;
    private final BookingRepository bookings;
    private final RentalOrderStatusHistoryRepository history;

    public static boolean isAllowed(OrderStatus from, OrderStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    public static Set<OrderStatus> nextStatuses(OrderStatus from) {
        return EnumSet.copyOf(ALLOWED.get(from));
    }

    /**
     * @param actor người thực hiện (khách / provider của đơn / admin)
     * @param note  ghi chú; với CANCELLED đây là lý do hủy
     */
    @Transactional
    public RentalOrder transition(RentalOrder order, OrderStatus to, User actor, String note) {
        OrderStatus from = order.getStatus();
        if (!isAllowed(from, to)) {
            throw new InvalidOrderTransitionException("Không thể chuyển đơn từ " + from + " sang " + to);
        }
        checkPermission(order, from, to, actor);

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(to);
        if (to == OrderStatus.COMPLETED) {
            order.setCompletedAt(now);
        }
        if (to == OrderStatus.CANCELLED) {
            order.setCancelledAt(now);
            order.setCancelledBy(actor);
            order.setCancellationReason(note);
        }
        orders.save(order);

        Booking booking = order.getBooking();
        if (booking != null) {
            booking.setStatus(toBookingStatus(to));
            if (to == OrderStatus.CANCELLED) {
                booking.setCancelledAt(now);
                booking.setCancelledBy(actor);
                booking.setCancellationReason(note);
            }
            bookings.save(booking);
        }

        history.save(RentalOrderStatusHistory.builder()
                .order(order)
                .previousStatus(from.name())
                .newStatus(to.name())
                .changedBy(actor)
                .note(note)
                .build());
        return order;
    }

    private void checkPermission(RentalOrder order, OrderStatus from, OrderStatus to, User actor) {
        if (actor == null) {
            throw new OrderAccessDeniedException("Không xác định được người thực hiện");
        }
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        boolean isCustomer = order.getCustomer() != null && Objects.equals(order.getCustomer().getId(), actor.getId());
        boolean isProvider = order.getProvider() != null && Objects.equals(order.getProvider().getId(), actor.getId());

        boolean allowed = switch (to) {
            case PAID -> isCustomer;
            case PROCESSING -> isProvider;
            case COMPLETED -> isProvider || isCustomer;
            case CANCELLED -> (isCustomer && (from == OrderStatus.PENDING_PAYMENT || from == OrderStatus.PAID))
                    || (isProvider && from == OrderStatus.PAID);
            case PENDING_PAYMENT -> false;
        };
        if (!allowed) {
            throw new OrderAccessDeniedException("Bạn không có quyền thực hiện thao tác này trên đơn hàng");
        }
    }

    private static BookingStatus toBookingStatus(OrderStatus s) {
        return switch (s) {
            case PENDING_PAYMENT -> BookingStatus.PENDING;
            case PAID -> BookingStatus.CONFIRMED;
            case PROCESSING -> BookingStatus.IN_PROGRESS;
            case COMPLETED -> BookingStatus.COMPLETED;
            case CANCELLED -> BookingStatus.CANCELLED;
        };
    }
}
