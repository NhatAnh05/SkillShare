package com.skillrental;

import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServicePricingType;
import com.skillrental.service.ServicePriceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServicePriceCalculatorTest {
    private final ServicePriceCalculator calculator = new ServicePriceCalculator();

    @Test
    void fixedPriceIsMultipliedByQuantity() {
        var result = calculator.calculate(service("250000", ServicePricingType.FIXED), 2, 2, false);

        assertEquals(new BigDecimal("250000.00"), result.unitPrice());
        assertEquals(new BigDecimal("500000.00"), result.baseTotal());
        assertEquals(new BigDecimal("0.00"), result.premiumSurcharge());
        assertEquals(new BigDecimal("500000.00"), result.total());
    }

    @Test
    void hourlyPriceUsesDurationAndQuantity() {
        var result = calculator.calculate(service("250000", ServicePricingType.HOURLY), 1, 2, false);

        assertEquals(new BigDecimal("500000.00"), result.baseTotal());
        assertEquals(new BigDecimal("500000.00"), result.total());
    }

    @Test
    void premiumAddsTwentyFivePercentToBaseTotal() {
        var result = calculator.calculate(service("250000", ServicePricingType.FIXED), 2, 2, true);

        assertEquals(new BigDecimal("500000.00"), result.baseTotal());
        assertEquals(new BigDecimal("125000.00"), result.premiumSurcharge());
        assertEquals(new BigDecimal("625000.00"), result.total());
    }

    private ServiceCatalog service(String price, ServicePricingType pricingType) {
        return ServiceCatalog.builder()
                .price(new BigDecimal(price))
                .pricingType(pricingType)
                .build();
    }
}
