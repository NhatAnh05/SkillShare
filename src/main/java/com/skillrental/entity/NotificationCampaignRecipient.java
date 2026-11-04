package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_campaign_recipients",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_campaign_recipient",
                columnNames = {"campaign_id", "recipient_id"}),
        indexes = @Index(name = "idx_notification_campaign_recipient_status", columnList = "campaign_id, delivery_status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationCampaignRecipient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private NotificationCampaign campaign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @OneToOne
    @JoinColumn(name = "notification_id", unique = true)
    private Notification notification;

    @Column(name = "delivery_status", nullable = false, length = 24)
    @Builder.Default
    private String deliveryStatus = "QUEUED";

    @Column(name = "websocket_attempts", nullable = false)
    @Builder.Default
    private int websocketAttempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
