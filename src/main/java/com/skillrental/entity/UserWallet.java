package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_wallets", uniqueConstraints = @UniqueConstraint(name = "uq_wallet_user", columnNames = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserWallet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, precision = 14, scale = 2) @Builder.Default private BigDecimal balance = BigDecimal.ZERO;
    @Column(name = "locked_amount", nullable = false, precision = 14, scale = 2) @Builder.Default private BigDecimal lockedAmount = BigDecimal.ZERO;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist void prePersist() {
        if (balance == null) balance = BigDecimal.ZERO;
        if (lockedAmount == null) lockedAmount = BigDecimal.ZERO;
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
