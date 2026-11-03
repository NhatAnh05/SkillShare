package com.skillrental;

import com.skillrental.service.AdminDisputeDecisionPolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class AdminDisputeDecisionPolicyTest {
    private static final BigDecimal HELD = new BigDecimal("900000.00");

    @Test void allowsZeroPartialAndFullAward() {
        assertEquals(new BigDecimal("0.00"), AdminDisputeDecisionPolicy.award(BigDecimal.ZERO, HELD));
        assertEquals(new BigDecimal("270000.00"), AdminDisputeDecisionPolicy.award(new BigDecimal("270000"), HELD));
        assertEquals(HELD, AdminDisputeDecisionPolicy.award(HELD, HELD));
    }
    @Test void rejectsOverpaymentNegativeAndTooManyDecimals() {
        assertThrows(IllegalArgumentException.class, () -> AdminDisputeDecisionPolicy.award(new BigDecimal("900000.01"), HELD));
        assertThrows(IllegalArgumentException.class, () -> AdminDisputeDecisionPolicy.award(new BigDecimal("-1"), HELD));
        assertThrows(IllegalArgumentException.class, () -> AdminDisputeDecisionPolicy.award(new BigDecimal("12.345"), HELD));
        assertThrows(IllegalArgumentException.class, () -> AdminDisputeDecisionPolicy.award(null, HELD));
        assertThrows(IllegalStateException.class, () -> AdminDisputeDecisionPolicy.award(BigDecimal.ZERO, BigDecimal.ZERO));
    }
    @Test void requiresDetailedReason() {
        assertThrows(IllegalArgumentException.class, () -> AdminDisputeDecisionPolicy.reason("Không đồng ý"));
        assertThrows(IllegalArgumentException.class, () -> AdminDisputeDecisionPolicy.reason("x".repeat(2001)));
        assertEquals("Đã kiểm tra tiến độ và bằng chứng.",
                AdminDisputeDecisionPolicy.reason("  Đã kiểm tra tiến độ và bằng chứng.  "));
    }
}
