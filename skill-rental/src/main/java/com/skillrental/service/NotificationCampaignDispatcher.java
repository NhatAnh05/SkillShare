package com.skillrental.service;

import com.skillrental.notification.NotificationCampaignQueuedEvent;
import com.skillrental.repository.NotificationCampaignRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@lombok.extern.slf4j.Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCampaignDispatcher {
    private final NotificationCampaignRecipientRepository recipients;
    private final NotificationCampaignDeliveryService delivery;

    @Async("notificationDispatchExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(NotificationCampaignQueuedEvent event) {
        long afterId = 0;
        while (true) {
            var batch = recipients.findTop100ByCampaignIdAndDeliveryStatusAndIdGreaterThanOrderByIdAsc(
                    event.campaignId(), "QUEUED", afterId);
            if (batch.isEmpty()) return;
            for (var row : batch) {
                afterId = row.getId();
                try {
                    delivery.deliverQueued(row.getId());
                } catch (RuntimeException ex) {
                    log.warn("Notification delivery failed for recipient row {}", row.getId());
                    try { delivery.recordCreationFailure(row.getId(), "Không thể lưu thông báo. Admin có thể thử lại."); }
                    catch (RuntimeException recordFailure) {
                        // Leave QUEUED for explicit retry; continue with the other recipients.
                        log.error("Cannot record delivery failure for recipient row {}", row.getId());
                    }
                }
            }
        }
    }
}
