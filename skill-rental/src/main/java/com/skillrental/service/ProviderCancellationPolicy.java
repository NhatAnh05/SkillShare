package com.skillrental.service;

import com.skillrental.entity.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

/** Validates Provider cancellation before ANY wallet changes. Fail closed on legacy/corrupt orders. */
public final class ProviderCancellationPolicy {
    private ProviderCancellationPolicy() {}

    private static final EnumSet<OrderStatus> ALLOWED = EnumSet.of(
            OrderStatus.PENDING_PROVIDER_ACCEPTANCE,
            OrderStatus.PENDING_START_CONFIRMATION,
            OrderStatus.PROCESSING,
            OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,
            OrderStatus.PENDING_PAYMENT_OTP,
            OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER,
            OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER,
            OrderStatus.CANCELLATION_DISPUTED);

    public static BigDecimal refundableAmount(OrderStatus status, BigDecimal held, BigDecimal total,
                                              boolean alreadySettled) {
        if (alreadySettled || status == null || !ALLOWED.contains(status)) {
            throw new IllegalStateException("Đơn đã kết thúc, đã quyết toán hoặc không hỗ trợ Provider hủy.");
        }
        BigDecimal escrow = normalized(held);
        BigDecimal agreed = normalized(total);
        if (escrow.signum() <= 0 || agreed.signum() <= 0 || escrow.compareTo(agreed) != 0) {
            throw new IllegalStateException("Tiền ký quỹ không bằng giá trị đơn; cần Admin đối soát trước khi hoàn tiền.");
        }
        return escrow;
    }

    private static BigDecimal normalized(BigDecimal value) {
        if (value == null) throw new IllegalStateException("Đơn thiếu thông tin tiền ký quỹ.");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) {
            throw new IllegalStateException("Số tiền không hợp lệ; cần Admin đối soát.", e);
        }
    }
}
