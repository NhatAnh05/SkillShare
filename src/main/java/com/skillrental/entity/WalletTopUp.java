package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_topups", uniqueConstraints = @UniqueConstraint(name = "uq_wallet_topup_ref", columnNames = "txn_ref"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTopUp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "txn_ref", nullable = false, unique = true, length = 80) private String txnRef;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "PENDING";
    @Column(name = "gateway_transaction_no", length = 80) private String gatewayTransactionNo;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
