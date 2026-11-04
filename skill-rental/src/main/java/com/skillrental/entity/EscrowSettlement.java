package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One immutable accounting snapshot per order settlement, not a second wallet. */
@Entity
@Table(name = "escrow_settlements", uniqueConstraints =
        @UniqueConstraint(name = "uq_escrow_settlement_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_escrow_settlement_time", columnList = "settled_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EscrowSettlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "order_id", nullable = false, unique = true)
    private RentalOrder order;
    @ManyToOne(optional = false) @JoinColumn(name = "settled_by_user_id", nullable = false)
    private User settledBy;
    @Column(nullable = false, length = 32) private String kind;
    @Column(name = "held_before", nullable = false, precision = 14, scale = 2) private BigDecimal heldBefore;
    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2) private BigDecimal grossAmount;
    @Column(name = "customer_refund_amount", nullable = false, precision = 14, scale = 2) private BigDecimal customerRefundAmount;
    @Column(name = "platform_fee_amount", nullable = false, precision = 14, scale = 2) private BigDecimal platformFeeAmount;
    @Column(name = "provider_net_amount", nullable = false, precision = 14, scale = 2) private BigDecimal providerNetAmount;
    @Column(name = "commission_rate", nullable = false, precision = 9, scale = 8) private BigDecimal commissionRate;
    @Column(name = "settled_at", nullable = false) private LocalDateTime settledAt;
    @PrePersist void prePersist() { if (settledAt == null) settledAt = LocalDateTime.now(); }

    public boolean isBalanced() {
        return heldBefore != null && grossAmount != null && customerRefundAmount != null
                && platformFeeAmount != null && providerNetAmount != null
                && heldBefore.compareTo(grossAmount.add(customerRefundAmount)) == 0
                && grossAmount.compareTo(platformFeeAmount.add(providerNetAmount)) == 0;
    }
}
