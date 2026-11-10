package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor public class OrderService {
    private final RentalOrderRepository orders;
    private final BookingRepository bookings;
    public List<RentalOrder> list(User u) {
        return orders.findByCustomerOrderByIdDesc(u);
    }
    public RentalOrder owned(Long id,User u) {
        var o=orders.findById(id).orElseThrow();
        if(!o.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Không có quyền");
        return o;
    }
    @Transactional public void processing(RentalOrder o) {
        if(o.getStatus()!=OrderStatus.PAID)throw new IllegalArgumentException("Đơn chưa thanh toán hoặc đã bắt đầu");
        o.setStatus(OrderStatus.PROCESSING);
        orders.save(o);
        var b=o.getBooking();
        b.setStatus(BookingStatus.IN_PROGRESS);
        bookings.save(b);
    }
    @Transactional public void saveLegacyCancellation(RentalOrder o) {
        orders.save(o);
        var b=o.getBooking();
        if(b!=null) {
            b.setStatus(BookingStatus.CANCELLED);
            b.setCancelledBy(o.getCancelledBy());
            b.setCancelledAt(o.getCancelledAt());
            b.setCancellationReason(o.getCancellationReason());
            bookings.save(b);
        }
    }
    @Transactional public void complete(RentalOrder o) {
        if(o.getStatus()!=OrderStatus.PROCESSING)throw new IllegalArgumentException("Đơn chưa trong giai đoạn thực hiện");
        o.setStatus(OrderStatus.COMPLETED);
        o.setCompletedAt(LocalDateTime.now());
        orders.save(o);
        var b=o.getBooking();
        b.setStatus(BookingStatus.COMPLETED);
        bookings.save(b);
    }
    public long completed(User u) {
        return orders.countByCustomerAndStatus(u,OrderStatus.COMPLETED);
    }
    public long processingCount(User u) {
        return orders.countByCustomerAndStatus(u,OrderStatus.PROCESSING);
    }
}
