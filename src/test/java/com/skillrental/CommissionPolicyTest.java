package com.skillrental;

import com.skillrental.service.CommissionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.test.util.ReflectionTestUtils;

class CommissionPolicyTest {
    private final CommissionPolicy policy = new CommissionPolicy();

    @Test
    void fullSettlementSplitsGrossIntoFivePercentCommissionAndNinetyFivePercentProviderNet() {
        BigDecimal gross = new BigDecimal("1000000.00");
        assertEquals(new BigDecimal("50000.00"), policy.fee(gross));
        assertEquals(new BigDecimal("950000.00"), policy.providerNet(gross));
    }

    @Test
    void invalidCommissionRateCannotProduceNegativeProviderEarnings() {
        ReflectionTestUtils.setField(policy, "rate", new BigDecimal("1.20"));
        assertThrows(IllegalStateException.class, () -> policy.fee(new BigDecimal("900000.00")));
    }

    @Test
    void partialCancellationUsesTheSameCommissionRate() {
        BigDecimal agreedCompensation = new BigDecimal("300000.00");
        assertEquals(new BigDecimal("15000.00"), policy.fee(agreedCompensation));
        assertEquals(new BigDecimal("285000.00"), policy.providerNet(agreedCompensation));
    }
}
