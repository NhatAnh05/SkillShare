package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="wallets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Wallet {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) @JoinColumn(name="user_id", unique=true) private User user;
    @Column(name="available_balance", nullable=false, precision=14, scale=2)
    @Builder.Default private BigDecimal availableBalance = BigDecimal.ZERO;
    @Column(name="held_balance", nullable=false, precision=14, scale=2)
    @Builder.Default private BigDecimal heldBalance = BigDecimal.ZERO;
    @Column(name="total_deposited", nullable=false, precision=14, scale=2)
    @Builder.Default private BigDecimal totalDeposited = BigDecimal.ZERO;
    @Column(name="total_withdrawn", nullable=false, precision=14, scale=2)
    @Builder.Default private BigDecimal totalWithdrawn = BigDecimal.ZERO;
    @Version private Long version;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @PrePersist void prePersist() {
        if(createdAt==null) createdAt=LocalDateTime.now();
        if(updatedAt==null) updatedAt=LocalDateTime.now();
        if(availableBalance==null) availableBalance=BigDecimal.ZERO;
        if(heldBalance==null) heldBalance=BigDecimal.ZERO;
        if(totalDeposited==null) totalDeposited=BigDecimal.ZERO;
        if(totalWithdrawn==null) totalWithdrawn=BigDecimal.ZERO;
    }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
