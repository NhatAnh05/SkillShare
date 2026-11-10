package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="wallet_transactions", indexes={
    @Index(name="idx_wallet_tx_wallet_created", columnList="wallet_id,created_at"),
    @Index(name="idx_wallet_tx_reference", columnList="reference_type,reference_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTransaction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="wallet_id", nullable=false) private Wallet wallet;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private WalletTransactionType type;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal amount;
    @Column(name="balance_after", nullable=false, precision=14, scale=2) private BigDecimal balanceAfter;
    @Column(name="held_after", nullable=false, precision=14, scale=2) private BigDecimal heldAfter;
    @Column(name="reference_type", length=30) private String referenceType;
    @Column(name="reference_id") private Long referenceId;
    @Column(nullable=false, length=500) private String description;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist void prePersist() {
        if(createdAt==null) createdAt=LocalDateTime.now();
        if(balanceAfter==null) balanceAfter=BigDecimal.ZERO;
        if(heldAfter==null) heldAfter=BigDecimal.ZERO;
        if(amount==null) amount=BigDecimal.ZERO;
    }
}
