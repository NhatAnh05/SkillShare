package com.skillrental;

import com.skillrental.service.CancellationCompensationPolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CancellationCompensationPolicyTest {
    @Test void validatesZeroExactHoldAndFractionalValues() {
        BigDecimal held = new BigDecimal("900000.00");
        assertEquals(new BigDecimal("0.00"), CancellationCompensationPolicy.amount(BigDecimal.ZERO, held));
        assertEquals(new BigDecimal("900000.00"), CancellationCompensationPolicy.amount(held, held));
        assertEquals(new BigDecimal("270000.25"), CancellationCompensationPolicy.amount(new BigDecimal("270000.25"), held));
    }
    @Test void rejectsMalformedAmountAndEvidence() {
        BigDecimal held = new BigDecimal("900000.00");
        for (BigDecimal n : new BigDecimal[]{null, new BigDecimal("-1"),
                new BigDecimal("900000.01"),new BigDecimal("0.001")}) {
            assertThrows(IllegalArgumentException.class, () -> CancellationCompensationPolicy.amount(n, held));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CancellationCompensationPolicy.description("Too short",20,2000,"Summary"));
        assertThrows(IllegalArgumentException.class,
                () -> CancellationCompensationPolicy.description(null,10,2000,"Evidence"));
        assertEquals("Đã có bằng chứng", CancellationCompensationPolicy.description("  Đã có bằng chứng  ",10,2000,"Evidence"));
    }
}
