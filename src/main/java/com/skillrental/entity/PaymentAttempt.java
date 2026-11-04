package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="payment_attempts", uniqueConstraints={
    @UniqueConstraint(name="uq_payment_attempt_number", columnNames={"payment_id","attempt_number"}),
    @UniqueConstraint(name="uq_payment_attempt_merchant_ref", columnNames="merchant_reference"),
    @UniqueConstraint(name="uq_payment_attempt_gateway_txn", columnNames={"gateway_name","gateway_transaction_id"}),
    @UniqueConstraint(name="uq_payment_attempt_idempotency", columnNames="idempotency_key")
}, indexes={
    @Index(name="idx_payment_attempt_payment_status", columnList="payment_id,status"),
    @Index(name="idx_payment_attempt_created", columnList="initiated_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="payment_id", nullable=false) private Payment payment;
    @Column(name="attempt_number", nullable=false) private Integer attemptNumber;
    @Column(name="gateway_name", nullable=false, length=40) private String gatewayName;
    @Column(name="merchant_reference", nullable=false, length=120) private String merchantReference;
    @Column(name="gateway_transaction_id", length=180) private String gatewayTransactionId;
    @Column(name="idempotency_key", length=120) private String idempotencyKey;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Column(nullable=false, length=3) @Builder.Default private String currency = "VND";
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) @Builder.Default private PaymentAttemptStatus status = PaymentAttemptStatus.CREATED;
    @Column(name="response_code", length=80) private String responseCode;
    @Column(name="failure_reason", length=1000) private String failureReason;
    @Column(name="initiated_at") private LocalDateTime initiatedAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @PrePersist void prePersist() {
        if (initiatedAt == null) initiatedAt = LocalDateTime.now();
        if (currency == null) currency = "VND";
        if (status == null) status = PaymentAttemptStatus.CREATED;
        if (attemptNumber == null) attemptNumber = 1;
    }
}
