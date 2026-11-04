package com.skillrental;

import com.skillrental.service.EscrowSettlementMath;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class EscrowSettlementMathTest {
    @Test void completeNineHundredThousandAtFivePercent() {
        var s = EscrowSettlementMath.calculate(new BigDecimal("900000"),
                new BigDecimal("900000"), new BigDecimal("0.05"));
        assertEquals(new BigDecimal("900000.00"), s.held());
        assertEquals(new BigDecimal("45000.00"), s.fee());
        assertEquals(new BigDecimal("855000.00"), s.providerNet());
        assertEquals(new BigDecimal("0.00"), s.refund());
    }
    @Test void cancellationPaysOnlyAgreedAmountAndRefundsRest() {
        var s = EscrowSettlementMath.calculate(new BigDecimal("900000"),
                new BigDecimal("270000"), new BigDecimal("0.05"));
        assertEquals(new BigDecimal("630000.00"), s.refund());
        assertEquals(new BigDecimal("13500.00"), s.fee());
        assertEquals(new BigDecimal("256500.00"), s.providerNet());
    }
    @Test void zeroCompensationRefundsAllWithoutCommission() {
        var s = EscrowSettlementMath.calculate(new BigDecimal("900000"),
                BigDecimal.ZERO, new BigDecimal("0.05"));
        assertEquals(new BigDecimal("900000.00"), s.refund());
        assertEquals(0, s.providerNet().signum());
        assertEquals(0, s.fee().signum());
    }
    @Test void roundsFeeHalfUpWithoutLosingCents() {
        var s = EscrowSettlementMath.calculate(new BigDecimal("100.01"),
                new BigDecimal("100.01"), new BigDecimal("0.05"));
        assertEquals(new BigDecimal("5.00"), s.fee());
        assertEquals(new BigDecimal("95.01"), s.providerNet());
        assertEquals(0, s.gross().compareTo(s.fee().add(s.providerNet())));
    }
    @Test void rejectsMissingHeldAndOverpayment() {
        assertThrows(IllegalStateException.class, () -> EscrowSettlementMath.calculate(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.05")));
        assertThrows(IllegalStateException.class, () -> EscrowSettlementMath.calculate(
                new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("0.05")));
    }
    @Test void rejectsNegativeAndInvalidRateBeforeWalletMutation() {
        assertThrows(IllegalStateException.class, () -> EscrowSettlementMath.calculate(
                new BigDecimal("100"), new BigDecimal("-1"), new BigDecimal("0.05")));
        assertThrows(IllegalStateException.class, () -> EscrowSettlementMath.calculate(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("1.01")));
    }
}
