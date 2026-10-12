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
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    public List<RentalOrder> list(User u) {
        return orders.findByCustomerOrderByIdDesc(u);
    }
    public RentalOrder owned(Long id,User u) {
        var o=orders.findById(id).orElseThrow();
        if(!o.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Không có quyền");
        return o;
    }
    private BigDecimal discount(BigDecimal s,String c) {
        if(c==null)return BigDecimal.ZERO;
        return switch(c.trim().toUpperCase()) {
            case "WELCOME10"->s.multiply(new BigDecimal("0.10"));
            case "SKILL20"->s.multiply(new BigDecimal("0.20"));
            default->BigDecimal.ZERO;
        }
        ;
    }
    @Transactional public Payment pay(RentalOrder o,String method,String coupon,boolean ok) {
        if(!ok)throw new IllegalArgumentException("Bạn phải đồng ý điều khoản thanh toán");
        var d=discount(o.getSubtotal(),coupon);
        o.setDiscount(d);
        o.setTotal(o.getSubtotal().subtract(d));
        o.setStatus(OrderStatus.PAID);
        orders.save(o);
        var b=o.getBooking();
        b.setStatus(BookingStatus.CONFIRMED);
        bookings.save(b);
        var p=payments.findByOrder(o).orElse(Payment.builder().order(o).build());
        p.setMethod(method);
        p.setCouponCode(coupon);
        p.setAmount(o.getTotal());
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(LocalDateTime.now());
        return payments.save(p);
    }
    @Transactional public void processing(RentalOrder o) {
        if(o.getStatus()!=OrderStatus.PAID)throw new IllegalArgumentException("Đơn chưa thanh toán hoặc đã bắt đầu");
        o.setStatus(OrderStatus.PROCESSING);
        orders.save(o);
        var b=o.getBooking();
        b.setStatus(BookingStatus.IN_PROGRESS);
        bookings.save(b);
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
