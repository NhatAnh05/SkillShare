package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="payments", uniqueConstraints=@UniqueConstraint(name="uq_payment_gateway_transaction", columnNames={"gateway_name","gateway_transaction_id"}), indexes=@Index(name="idx_payments_status_paid_at", columnList="status,paid_at")) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) private RentalOrder order;
    @Column(nullable=false, length=40) private String method;
    @Column(name="gateway_name", length=40) private String gatewayName;
    @Column(name="gateway_transaction_id", length=180) private String gatewayTransactionId;
    @Column(name="idempotency_key", length=120, unique=true) private String idempotencyKey;
    @Column(name="coupon_code", length=80) private String couponCode;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Column(nullable=false, length=3) @Builder.Default private String currency = "VND";
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private PaymentStatus status;
    @Column(name="failure_reason", length=1000) private String failureReason;
    @Column(name="paid_at") private LocalDateTime paidAt;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @OneToMany(mappedBy="payment", cascade=CascadeType.ALL, orphanRemoval=true)
    @Builder.Default private List<PaymentAttempt> attempts = new ArrayList<>();
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=LocalDateTime.now(); if(currency==null) currency="VND"; }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
