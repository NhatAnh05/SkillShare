package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.exception.OrderAccessDeniedException;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor public class OrderService {
    private final RentalOrderRepository orders;
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final OrderStatusService orderStatus;
    public List<RentalOrder> list(User u) {
        return orders.findByCustomerOrderByIdDesc(u);
    }
    public RentalOrder owned(Long id,User u) {
        var o=orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng"));
        if(!o.getCustomer().getId().equals(u.getId()))throw new OrderAccessDeniedException("Không có quyền");
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
        orderStatus.transition(o,OrderStatus.PAID,o.getCustomer(),"Thanh toán thành công");
        var d=discount(o.getSubtotal(),coupon);
        o.setDiscount(d);
        o.setTotal(o.getSubtotal().subtract(d));
        orders.save(o);
        var p=payments.findByOrder(o).orElse(Payment.builder().order(o).build());
        p.setMethod(method);
        p.setCouponCode(coupon);
        p.setAmount(o.getTotal());
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(LocalDateTime.now());
        return payments.save(p);
    }
    @Transactional public void complete(RentalOrder o,User actor) {
        orderStatus.transition(o,OrderStatus.COMPLETED,actor,"Xác nhận hoàn thành");
    }
    public long completed(User u) {
        return orders.countByCustomerAndStatus(u,OrderStatus.COMPLETED);
    }
    public long processingCount(User u) {
        return orders.countByCustomerAndStatus(u,OrderStatus.PROCESSING);
    }
}
