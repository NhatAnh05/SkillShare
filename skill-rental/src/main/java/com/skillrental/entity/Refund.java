package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="refunds", indexes={
    @Index(name="idx_refunds_payment_status", columnList="payment_id,status"),
    @Index(name="idx_refunds_status_requested", columnList="status,requested_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Refund {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="payment_id", nullable=false) private Payment payment;
    @ManyToOne(optional=false) @JoinColumn(name="requested_by_user_id", nullable=false) private User requestedBy;
    @ManyToOne @JoinColumn(name="processed_by_admin_id") private User processedByAdmin;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Column(nullable=false, length=1200) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private RefundStatus status = RefundStatus.REQUESTED;
    @Column(name="gateway_refund_id", length=180, unique=true) private String gatewayRefundId;
    @Column(name="admin_note", length=1200) private String adminNote;
    @Column(name="requested_at") private LocalDateTime requestedAt;
    @Column(name="processed_at") private LocalDateTime processedAt;
    @PrePersist void prePersist() { if(requestedAt==null) requestedAt=LocalDateTime.now(); if(status==null) status=RefundStatus.REQUESTED; }
}
