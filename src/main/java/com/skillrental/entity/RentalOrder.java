package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="rental_orders") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RentalOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=true) @JoinColumn(name="booking_id", unique=true) private Booking booking;
    @ManyToOne(optional=false) private User customer;
    @ManyToOne(optional=true) private User provider;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal subtotal;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal discount;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=50) private OrderStatus status;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="cancellation_reason", length=1000) private String cancellationReason;
    @ManyToOne @JoinColumn(name="cancelled_by_user_id") private User cancelledBy;
    @Column(name="locked_amount", nullable=false, precision=12, scale=2, columnDefinition="DECIMAL(12,2) DEFAULT 0") @Builder.Default private BigDecimal lockedAmount = BigDecimal.ZERO;
    @Column(name="provider_accepted", nullable=false, columnDefinition="BOOLEAN DEFAULT FALSE") @Builder.Default private Boolean providerAccepted = false;
    @Column(name="customer_started", nullable=false, columnDefinition="BOOLEAN DEFAULT FALSE") @Builder.Default private Boolean customerStarted = false;
    @Column(name="provider_started", nullable=false, columnDefinition="BOOLEAN DEFAULT FALSE") @Builder.Default private Boolean providerStarted = false;
    @Column(name="start_deposit_amount", precision=12, scale=2) private BigDecimal startDepositAmount;
    @Column(name="provider_completed", nullable=false, columnDefinition="BOOLEAN DEFAULT FALSE") @Builder.Default private Boolean providerCompleted = false;
    @Column(name="customer_accepted_completion", nullable=false, columnDefinition="BOOLEAN DEFAULT FALSE") @Builder.Default private Boolean customerAcceptedCompletion = false;
    @Column(name="completion_otp_hash", length=100) private String completionOtpHash;
    @Column(name="completion_otp_expires_at") private LocalDateTime completionOtpExpiresAt;
    @Column(name="completion_otp_attempts", nullable=false, columnDefinition="INT DEFAULT 0") @Builder.Default private Integer completionOtpAttempts = 0;
    @Column(name="cancellation_compensation", precision=12, scale=2) private BigDecimal cancellationCompensation;
    @Column(name="cancellation_started_at") private LocalDateTime cancellationStartedAt;
    @Column(name="settled_at") private LocalDateTime settledAt;
    @Column(name="platform_fee_amount", precision=12, scale=2) private BigDecimal platformFeeAmount;
    @Column(name="provider_net_amount", precision=12, scale=2) private BigDecimal providerNetAmount;
    @OneToMany(mappedBy="order", cascade=CascadeType.ALL, orphanRemoval=true)
    @Builder.Default private List<RentalOrderItem> items = new ArrayList<>();
}
