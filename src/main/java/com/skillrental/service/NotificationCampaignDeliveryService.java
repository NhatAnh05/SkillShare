package com.skillrental.service;

import com.skillrental.entity.NotificationCampaignRecipient;
import com.skillrental.notification.NotificationCreatedEvent;
import com.skillrental.repository.NotificationCampaignRecipientRepository;
import com.skillrental.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCampaignDeliveryService {
    private final NotificationCampaignRecipientRepository recipients;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void deliverQueued(Long recipientId) {
        NotificationCampaignRecipient row = recipients.findByIdForUpdate(recipientId).orElse(null);
        if (row == null || !"QUEUED".equals(row.getDeliveryStatus())) return;
        var campaign = row.getCampaign();
        var notification = notificationService.create(row.getRecipient(), campaign.getSender(),
                campaign.getType(), campaign.getTitle(), campaign.getContent(),
                "ADMIN_ANNOUNCEMENT", campaign.getId(), campaign.getTargetUrl());
        row.setNotification(notification);
        row.setDeliveryStatus("STORED");
        row.setLastError(null);
        recipients.save(row);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordCreationFailure(Long recipientId, String message) {
        recipients.findByIdForUpdate(recipientId).ifPresent(row -> {
            if (row.getNotification() == null) {
                row.setDeliveryStatus("FAILED");
                row.setLastError(safeError(message));
                recipients.save(row);
            }
        });
    }

    @Transactional
    public void retryWebSocket(Long recipientId) {
        recipients.findByIdForUpdate(recipientId).ifPresent(row -> {
            if (row.getNotification() != null && "WS_FAILED".equals(row.getDeliveryStatus())) {
                row.setDeliveryStatus("STORED");
                row.setLastError(null);
                recipients.save(row);
                events.publishEvent(new NotificationCreatedEvent(row.getNotification().getId(),
                        row.getRecipient().getId()));
            }
        });
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordWebSocketAttempt(Long notificationId, int successfulSessions, int failedSessions) {
        recipients.findByNotificationId(notificationId).ifPresent(row -> {
            row.setWebsocketAttempts(row.getWebsocketAttempts() + 1);
            if (successfulSessions > 0) {
                row.setDeliveryStatus("ONLINE");
                row.setLastError(null);
            } else if (failedSessions > 0) {
                row.setDeliveryStatus("WS_FAILED");
                row.setLastError("Gửi WebSocket thất bại; thông báo vẫn được lưu trong hộp thư.");
            } else {
                row.setDeliveryStatus("OFFLINE");
                row.setLastError(null);
            }
            recipients.save(row);
        });
    }

    private String safeError(String message) {
        if (message == null || message.isBlank()) return "Không thể tạo thông báo cho người nhận.";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
