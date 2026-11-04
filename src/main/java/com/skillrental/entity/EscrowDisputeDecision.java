package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One auditable Admin decision per contested order, separate from Provider's proposal. */
@Entity
@Table(name = "escrow_dispute_decisions", uniqueConstraints =
        @UniqueConstraint(name = "uq_escrow_dispute_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_escrow_dispute_decided_at", columnList = "decided_at"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class EscrowDisputeDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true)
    private RentalOrder order;
    @ManyToOne(optional = false) @JoinColumn(name = "admin_id", nullable = false)
    private User admin;
    @Column(name = "provider_proposed", precision = 14, scale = 2) private BigDecimal providerProposed;
    @Column(name = "provider_awarded", nullable = false, precision = 14, scale = 2) private BigDecimal providerAwarded;
    @Column(name = "customer_refunded", nullable = false, precision = 14, scale = 2) private BigDecimal customerRefunded;
    @Column(name = "decision_reason", nullable = false, length = 2000) private String decisionReason;
    @Column(name = "decided_at", nullable = false) private LocalDateTime decidedAt;
    @PrePersist void prePersist() { if (decidedAt == null) decidedAt = LocalDateTime.now(); }
}
