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
    public RentalOrder involved(Long id, User u) {
        var order = orders.findById(id).orElseThrow();
        boolean customer = order.getCustomer() != null && order.getCustomer().getId().equals(u.getId());
        boolean provider = order.getProvider() != null && order.getProvider().getId().equals(u.getId());
        if (!customer && !provider) throw new IllegalArgumentException("Bạn không tham gia đơn hàng này.");
        return order;
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
        throw new IllegalStateException("Thanh toán trực tiếp đã bị vô hiệu hóa. Đơn phải được ký quỹ và chỉ quyết toán sau nghiệm thu + OTP.");
    }
    @Transactional public void processing(RentalOrder o) {
        throw new IllegalStateException("Không được chuyển trạng thái trực tiếp. Hãy dùng luồng Provider xác nhận nhận việc, hai bên xác nhận bắt đầu, rồi nghiệm thu và OTP.");
    }
    @Transactional public void complete(RentalOrder o) {
        throw new IllegalStateException("Không được đánh dấu hoàn thành/quyết toán trực tiếp. Cần Provider báo hoàn thành, Customer nghiệm thu và xác nhận OTP.");
    }
    public long completed(User u) {
        return orders.countByCustomerAndStatus(u,OrderStatus.COMPLETED);
    }
    public long processingCount(User u) {
        return orders.countByCustomerAndStatus(u,OrderStatus.PROCESSING);
    }
}
