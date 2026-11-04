package com.skillrental.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name = "notification_campaign_audits")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationCampaignAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "campaign_id", nullable = false) private NotificationCampaign campaign;
    @ManyToOne(optional = false) @JoinColumn(name = "actor_id", nullable = false) private User actor;
    @Column(nullable = false, length = 16) private String action;
    @Column(name = "recipient_count", nullable = false) private int recipientCount;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void stamp() { createdAt = LocalDateTime.now(); }
}
