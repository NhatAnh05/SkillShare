package com.skillrental.service;

import com.skillrental.entity.OrderStatus;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.RentalOrderStatusHistory;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.RentalOrderRepository;
import com.skillrental.repository.RentalOrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderManagementService {
    private final RentalOrderRepository orders;
    private final RentalOrderStatusHistoryRepository history;

    @Transactional
    public void recordReview(Long orderId, User admin, String note) {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new OrderReviewException("Chỉ Admin mới được ghi nhận rà soát đơn hàng.");
        }
        if (note == null || note.isBlank()) {
            throw new OrderReviewException("Ghi chú rà soát không được để trống.");
        }
        String normalizedNote = note.trim();
        if (normalizedNote.length() > 1000) {
            throw new OrderReviewException("Ghi chú rà soát không được vượt quá 1000 ký tự.");
        }

        RentalOrder order = orders.findById(orderId)
                .orElseThrow(() -> new OrderReviewException("Không tìm thấy đơn hàng."));
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == null) {
            throw new OrderReviewException("Đơn hàng chưa có trạng thái hợp lệ để rà soát.");
        }

        history.findFirstByOrderIdOrderByCreatedAtDesc(orderId).ifPresent(latest -> {
            boolean duplicate = admin.getId().equals(latest.getChangedBy() == null ? null : latest.getChangedBy().getId())
                    && currentStatus.name().equals(latest.getPreviousStatus())
                    && currentStatus.name().equals(latest.getNewStatus())
                    && normalizedNote.equals(latest.getNote());
            if (duplicate) {
                throw new OrderReviewException("Yêu cầu rà soát giống hệt lần gần nhất đã được ghi nhận.");
            }
        });

        history.saveAndFlush(RentalOrderStatusHistory.builder()
                .order(order)
                .previousStatus(currentStatus.name())
                .newStatus(currentStatus.name())
                .changedBy(admin)
                .note(normalizedNote)
                .build());
    }

    public static class OrderReviewException extends RuntimeException {
        public OrderReviewException(String message) {
            super(message);
        }
    }
}
