package com.skillrental.entity;

public enum OrderStatus {
    PENDING_PAYMENT("Đơn cũ: chờ thanh toán"),
    PAID("Đơn cũ: đã thanh toán"),
    PENDING_PROVIDER_ACCEPTANCE("Chờ Provider xác nhận"),
    PENDING_START_CONFIRMATION("Chờ hai bên xác nhận bắt đầu"),
    PROCESSING("Đang thực hiện"),
    PENDING_CUSTOMER_ACCEPTANCE("Chờ Customer nghiệm thu"),
    PENDING_PAYMENT_OTP("Chờ OTP quyết toán"),
    CANCELLATION_REQUESTED_BY_CUSTOMER("Chờ Provider đề xuất chi phí hủy"),
    CANCELLATION_COMPENSATION_PENDING_CUSTOMER("Chờ Customer xác nhận chi phí hủy"),
    COMPLETED("Đã hoàn thành và quyết toán"),
    CANCELLED("Đã hủy");

    private final String displayName;
    OrderStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
