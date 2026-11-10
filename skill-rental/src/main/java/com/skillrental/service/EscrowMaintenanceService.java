package com.skillrental.service;

import com.skillrental.entity.Escrow;
import com.skillrental.entity.EscrowStatus;
import com.skillrental.entity.RentalOrder;
import com.skillrental.repository.EscrowRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Auto-resolves escrows stuck in a waiting state so no order hangs forever:
 * - HELD too long without both sides confirming start -> cancel and refund 100%.
 * - AWAITING_CONFIRMATION too long without the customer entering OTP -> auto-settle to the provider.
 * - CANCELLATION_REQUESTED too long without provider response -> refund the remaining held amount.
 */
@Service @RequiredArgsConstructor
public class EscrowMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(EscrowMaintenanceService.class);

    private final EscrowRepository escrows;
    private final EscrowService escrowService;

    @Value("${app.escrow.hold-expiry-days:7}")
    private int holdExpiryDays;

    @Value("${app.escrow.confirm-expiry-days:3}")
    private int confirmExpiryDays;

    @Value("${app.escrow.cancel-response-days:3}")
    private int cancelResponseDays;

    /** Runs every 5 minutes so the timeout policies are visible during a demo. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void sweepStaleEscrows() {
        SweepResult result = runSweep();
        if (result.totalActions() > 0) {
            log.info("Escrow maintenance: cancelled {} stale holds, auto-released {} orders, auto-refunded {} cancellations.",
                    result.cancelledHolds(), result.autoReleased(), result.autoRefunded());
        }
    }

    public SweepResult runSweep() {
        int cancelledHolds = 0;
        int autoReleased = 0;
        int autoRefunded = 0;

        List<Escrow> active = escrows.findByStatusIn(List.of(
                EscrowStatus.HELD, EscrowStatus.AWAITING_CONFIRMATION, EscrowStatus.CANCELLATION_REQUESTED));
        for (Escrow escrow : active) {
            RentalOrder order = escrow.getOrder();
            if (order == null) continue;
            try {
                switch (escrow.getStatus()) {
                    case HELD -> {
                        if (isStale(escrow.getUpdatedAt(), holdExpiryDays)) {
                            escrowService.cancelBeforeStart(order, escrow.getCustomer(),
                                    "Hệ thống tự động hủy: quá " + holdExpiryDays
                                            + " ngày không có xác nhận bắt đầu từ hai bên.");
                            cancelledHolds++;
                        }
                    }
                    case AWAITING_CONFIRMATION -> {
                        LocalDateTime since = escrow.getProviderCompletedAt() != null
                                ? escrow.getProviderCompletedAt() : escrow.getUpdatedAt();
                        if (isStale(since, confirmExpiryDays)) {
                            escrowService.autoRelease(order,
                                    "Quá " + confirmExpiryDays + " ngày khách không nhập OTP xác nhận; hệ thống tự động tất toán escrow cho Provider.");
                            autoReleased++;
                        }
                    }
                    case CANCELLATION_REQUESTED -> {
                        if (isStale(escrow.getUpdatedAt(), cancelResponseDays)) {
                            escrowService.autoRefundCancellation(order,
                                    "Hệ thống tự động xử lý hủy: quá " + cancelResponseDays
                                            + " ngày Provider không phản hồi yêu cầu hủy.");
                            autoRefunded++;
                        }
                    }
                    default -> { }
                }
            } catch (Exception ex) {
                log.warn("Escrow maintenance failed for order #{}: {}", order.getId(), ex.getMessage());
            }
        }
        return new SweepResult(cancelledHolds, autoReleased, autoRefunded);
    }

    private boolean isStale(LocalDateTime at, int days) {
        if (at == null) return false;
        long safeDays = Math.max(0, days);
        return at.isBefore(LocalDateTime.now().minusDays(safeDays));
    }

    public int holdExpiryDays() { return holdExpiryDays; }
    public int confirmExpiryDays() { return confirmExpiryDays; }
    public int cancelResponseDays() { return cancelResponseDays; }

    public record SweepResult(int cancelledHolds, int autoReleased, int autoRefunded) {
        public int totalActions() { return cancelledHolds + autoReleased + autoRefunded; }
    }
}
