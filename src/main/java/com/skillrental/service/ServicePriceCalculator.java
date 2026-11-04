package com.skillrental.service;

import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServicePricingType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ServicePriceCalculator {
    public static final int DEFAULT_DURATION_HOURS = 2;
    public static final BigDecimal PREMIUM_RATE = new BigDecimal("0.25");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public PriceBreakdown calculate(ServiceCatalog service, int quantity, int durationHours, boolean premium) {
        if (service == null || service.getPrice() == null) {
            throw new IllegalArgumentException("Không xác định được giá dịch vụ.");
        }
        return calculate(service.getPrice(), service.getPricingType(), quantity, durationHours, premium);
    }

    public PriceBreakdown calculate(BigDecimal unitPrice, ServicePricingType pricingType, int quantity,
                                    int durationHours, boolean premium) {
        if (unitPrice == null || pricingType == null) {
            throw new IllegalArgumentException("Không xác định được giá dịch vụ.");
        }
        if (quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException("Số lượng phải từ 1 đến 99.");
        }
        if (durationHours < 1 || durationHours > 24) {
            throw new IllegalArgumentException("Thời lượng phải từ 1 đến 24 giờ.");
        }

        BigDecimal normalizedUnitPrice = money(unitPrice);
        BigDecimal multiplier = BigDecimal.valueOf(quantity);
        if (pricingType == ServicePricingType.HOURLY) {
            multiplier = multiplier.multiply(BigDecimal.valueOf(durationHours));
        }
        BigDecimal baseTotal = money(normalizedUnitPrice.multiply(multiplier));
        BigDecimal premiumSurcharge = premium
                ? money(baseTotal.multiply(PREMIUM_RATE))
                : ZERO;
        return new PriceBreakdown(normalizedUnitPrice, baseTotal, premiumSurcharge,
                money(baseTotal.add(premiumSurcharge)));
    }

    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record PriceBreakdown(BigDecimal unitPrice, BigDecimal baseTotal,
                                 BigDecimal premiumSurcharge, BigDecimal total) {}
}
