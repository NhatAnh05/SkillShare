package com.skillrental;

import com.skillrental.entity.OrderStatus;
import com.skillrental.service.ProviderCancellationPolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ProviderCancellationPolicyTest {
    private static final BigDecimal TOTAL = new BigDecimal("900000.00");

    @Test void allUnsettledEscrowStatusesAllowFullRefund() {
        for (OrderStatus s : new OrderStatus[] {
                OrderStatus.PENDING_PROVIDER_ACCEPTANCE, OrderStatus.PENDING_START_CONFIRMATION,
                OrderStatus.PROCESSING, OrderStatus.PENDING_CUSTOMER_ACCEPTANCE,
                OrderStatus.PENDING_PAYMENT_OTP, OrderStatus.CANCELLATION_REQUESTED_BY_CUSTOMER,
                OrderStatus.CANCELLATION_COMPENSATION_PENDING_CUSTOMER, OrderStatus.CANCELLATION_DISPUTED}) {
            assertEquals(TOTAL, ProviderCancellationPolicy.refundableAmount(s, TOTAL, TOTAL, false), s.name());
        }
    }

    @Test void rejectSettledAndTerminalOrders() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThrows(IllegalStateException.class,
                    () -> ProviderCancellationPolicy.refundableAmount(s, TOTAL, TOTAL, true));
        }
        for (OrderStatus s : new OrderStatus[] {OrderStatus.COMPLETED, OrderStatus.CANCELLED,
                OrderStatus.PAID, OrderStatus.PENDING_PAYMENT}) {
            assertThrows(IllegalStateException.class,
                    () -> ProviderCancellationPolicy.refundableAmount(s, TOTAL, TOTAL, false));
        }
    }

    @Test void rejectMissingZeroNegativePartialOrOverPrecisionHold() {
        for (BigDecimal value : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("450000.00"), new BigDecimal("900001.00"), new BigDecimal("900000.001")}) {
            assertThrows(IllegalStateException.class,
                    () -> ProviderCancellationPolicy.refundableAmount(OrderStatus.PROCESSING, value, TOTAL, false));
        }
    }
}
