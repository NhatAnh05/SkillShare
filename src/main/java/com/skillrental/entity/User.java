package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="users") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String fullName;
    @Column(nullable=false,unique=true,length=160) private String email;
    @Column(nullable=false) private String password;
    @Column(length=20) private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role;
    @Column(length=500) private String avatarUrl;
    @Column(length=1200) private String bio;
    @Column(length=120) private String resetToken;
    @Column(name="reset_token_expires_at") private LocalDateTime resetTokenExpiresAt;
    @Column(name="email_verified", nullable=false) @Builder.Default private Boolean emailVerified = false;
    @Column(name="email_verification_token", length=120, unique=true) private String emailVerificationToken;
    @Column(name="email_verification_expires_at") private LocalDateTime emailVerificationExpiresAt;
    // Registration OTP is stored as a BCrypt hash in emailVerificationToken (legacy column).
    @Column(name="email_verification_attempts", nullable=false)
    @Builder.Default private Integer emailVerificationAttempts = 0;
    @Column(name="email_verification_last_sent_at") private LocalDateTime emailVerificationLastSentAt;
    @Enumerated(EnumType.STRING) @Column(name="account_status", nullable=false, length=24)
    @Builder.Default private AccountStatus accountStatus = AccountStatus.ACTIVE;
    @Column(name="last_login_at") private LocalDateTime lastLoginAt;
    @Column(name="two_factor_preference", nullable=false) @Builder.Default private Boolean twoFactorPreference = false;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @PrePersist void pre() {
        if(createdAt==null) createdAt=LocalDateTime.now();
        if(updatedAt==null) updatedAt=LocalDateTime.now();
        if(accountStatus==null) accountStatus=AccountStatus.ACTIVE;
        if(emailVerified==null) emailVerified=false;
        if(emailVerificationAttempts==null) emailVerificationAttempts=0;
    }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
