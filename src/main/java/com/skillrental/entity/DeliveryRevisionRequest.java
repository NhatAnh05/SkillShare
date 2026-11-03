package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_revision_requests", indexes = @Index(name = "idx_delivery_revision_order", columnList = "order_id,created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryRevisionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RentalOrder order;
    @ManyToOne(optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private DeliverySubmission delivery;
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;
    @Column(nullable = false, length = 2000)
    private String reason;
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
