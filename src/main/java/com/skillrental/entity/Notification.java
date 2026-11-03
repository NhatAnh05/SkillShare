package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id,created_at"),
        @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id,read_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;
    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, length = 1200)
    private String content;
    @Column(name = "target_type", length = 40)
    private String targetType;
    @Column(name = "target_id")
    private Long targetId;
    @Column(name = "target_url", nullable = false, length = 500)
    private String targetUrl;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
