package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_tx_wallet_date", columnList = "wallet_id,created_at"),
        @Index(name = "idx_wallet_tx_order", columnList = "order_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "wallet_id", nullable = false) private UserWallet wallet;
    @ManyToOne @JoinColumn(name = "order_id") private RentalOrder order;
    @Column(nullable = false, length = 32) private String type;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, precision = 14, scale = 2) private BigDecimal balanceAfter;
    @Column(name = "locked_after", nullable = false, precision = 14, scale = 2) private BigDecimal lockedAfter;
    @Column(nullable = false, length = 800) private String description;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
