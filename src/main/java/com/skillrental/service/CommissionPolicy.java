package com.skillrental.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One fee policy shared by settlement, Provider income and Admin finance screens. */
@Component
public class CommissionPolicy {
    @Value("${skillshare.commission.rate:0.05}")
    private BigDecimal rate;

    public BigDecimal rate() {
        return rate == null ? new BigDecimal("0.05") : rate;
    }

    public BigDecimal fee(BigDecimal grossAmount) {
        if (grossAmount == null || grossAmount.signum() <= 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return grossAmount.multiply(rate()).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal providerNet(BigDecimal grossAmount) {
        BigDecimal gross = grossAmount == null ? BigDecimal.ZERO : grossAmount;
        return gross.subtract(fee(gross)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal grossSettledAmount(com.skillrental.entity.RentalOrder order) {
        if (order == null) return BigDecimal.ZERO;
        if (order.getStatus() == com.skillrental.entity.OrderStatus.CANCELLED) {
            return order.getCancellationCompensation() == null ? BigDecimal.ZERO : order.getCancellationCompensation();
        }
        return order.getTotal() == null ? BigDecimal.ZERO : order.getTotal();
    }

    public BigDecimal feeFor(com.skillrental.entity.RentalOrder order) {
        if (order == null || order.getSettledAt() == null) return BigDecimal.ZERO;
        if (order.getPlatformFeeAmount() != null && order.getPlatformFeeAmount().signum() >= 0) return order.getPlatformFeeAmount();
        return fee(grossSettledAmount(order));
    }

    public BigDecimal providerNetFor(com.skillrental.entity.RentalOrder order) {
        if (order == null || order.getSettledAt() == null) return BigDecimal.ZERO;
        if (order.getProviderNetAmount() != null && order.getProviderNetAmount().signum() >= 0) return order.getProviderNetAmount();
        return providerNet(grossSettledAmount(order));
    }
}
