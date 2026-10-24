package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_withdrawals", indexes = {
        @Index(name = "idx_provider_withdrawal_status_created", columnList = "status,created_at"),
        @Index(name = "idx_provider_withdrawal_provider", columnList = "provider_id,created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderWithdrawal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "provider_id", nullable = false) private User provider;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "bank_name", nullable = false, length = 100) private String bankName;
    @Column(name = "account_number", nullable = false, length = 80) private String accountNumber;
    @Column(name = "account_holder", nullable = false, length = 120) private String accountHolder;
    @Column(nullable = false, length = 30) @Builder.Default private String status = "PENDING_OTP";
    @Column(name = "otp_hash", length = 100) private String otpHash;
    @Column(name = "otp_expires_at") private LocalDateTime otpExpiresAt;
    @Column(name = "otp_attempts", nullable = false) @Builder.Default private Integer otpAttempts = 0;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "verified_at") private LocalDateTime verifiedAt;
    @Column(name = "processed_at") private LocalDateTime processedAt;
    @ManyToOne @JoinColumn(name = "processed_by_admin_id") private User processedByAdmin;
    @Column(name = "admin_note", length = 600) private String adminNote;

    @PrePersist void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING_OTP";
        if (otpAttempts == null) otpAttempts = 0;
    }
}
