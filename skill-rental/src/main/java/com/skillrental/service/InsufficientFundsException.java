package com.skillrental.service;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientFundsException(BigDecimal required, BigDecimal available) {
        super("Số dư ví không đủ. Cần " + required + "đ nhưng chỉ có " + available + "đ.");
        this.required = required;
        this.available = available;
    }

    public BigDecimal getRequired() { return required; }
    public BigDecimal getAvailable() { return available; }
}
