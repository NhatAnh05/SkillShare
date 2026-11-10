package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="payment_otps", indexes={
    @Index(name="idx_payment_otp_order_created", columnList="order_id,created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentOtp {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="order_id", nullable=false) private RentalOrder order;
    @ManyToOne(optional=false) @JoinColumn(name="user_id", nullable=false) private User user;
    @Column(name="code_hash", nullable=false, length=255) private String codeHash;
    @Column(nullable=false, length=60) private String purpose;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(nullable=false) @Builder.Default private Boolean consumed = false;
    @Column(nullable=false) @Builder.Default private Integer attempts = 0;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist void prePersist() {
        if(createdAt==null) createdAt=LocalDateTime.now();
        if(consumed==null) consumed=false;
        if(attempts==null) attempts=0;
    }
}
