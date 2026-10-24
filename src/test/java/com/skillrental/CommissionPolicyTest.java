package com.skillrental;

import com.skillrental.service.CommissionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionPolicyTest {
    private final CommissionPolicy policy = new CommissionPolicy();

    @Test
    void fullSettlementSplitsGrossIntoFivePercentCommissionAndNinetyFivePercentProviderNet() {
        BigDecimal gross = new BigDecimal("1000000.00");
        assertEquals(new BigDecimal("50000.00"), policy.fee(gross));
        assertEquals(new BigDecimal("950000.00"), policy.providerNet(gross));
    }

    @Test
    void partialCancellationUsesTheSameCommissionRate() {
        BigDecimal agreedCompensation = new BigDecimal("300000.00");
        assertEquals(new BigDecimal("15000.00"), policy.fee(agreedCompensation));
        assertEquals(new BigDecimal("285000.00"), policy.providerNet(agreedCompensation));
    }
}
