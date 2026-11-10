package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="escrows", indexes={
    @Index(name="idx_escrows_status_created", columnList="status,created_at"),
    @Index(name="idx_escrows_provider", columnList="provider_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Escrow {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) @JoinColumn(name="order_id", unique=true) private RentalOrder order;
    @ManyToOne(optional=false) @JoinColumn(name="customer_id", nullable=false) private User customer;
    @ManyToOne(optional=false) @JoinColumn(name="provider_id", nullable=false) private User provider;
    @Column(name="total_amount", nullable=false, precision=14, scale=2) private BigDecimal totalAmount;
    @Column(name="released_amount", nullable=false, precision=14, scale=2)
    @Builder.Default private BigDecimal releasedAmount = BigDecimal.ZERO;
    @Column(name="customer_start_confirmed", nullable=false) @Builder.Default private Boolean customerStartConfirmed = false;
    @Column(name="provider_start_confirmed", nullable=false) @Builder.Default private Boolean providerStartConfirmed = false;
    @Column(name="customer_start_confirmed_at") private LocalDateTime customerStartConfirmedAt;
    @Column(name="provider_start_confirmed_at") private LocalDateTime providerStartConfirmedAt;
    @Column(name="started_at") private LocalDateTime startedAt;
    @Column(name="advance_released", nullable=false) @Builder.Default private Boolean advanceReleased = false;
    @Column(name="provider_completed_at") private LocalDateTime providerCompletedAt;
    @Column(name="customer_accepted_at") private LocalDateTime customerAcceptedAt;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @ManyToOne @JoinColumn(name="cancelled_by_user_id") private User cancelledBy;
    @Column(name="cancellation_reason", length=1000) private String cancellationReason;
    @Column(name="work_summary", length=1500) private String workSummary;
    @Column(name="compensation_amount", precision=14, scale=2) private BigDecimal compensationAmount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private EscrowStatus status;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @PrePersist void prePersist() {
        if(createdAt==null) createdAt=LocalDateTime.now();
        if(updatedAt==null) updatedAt=LocalDateTime.now();
        if(releasedAmount==null) releasedAmount=BigDecimal.ZERO;
        if(customerStartConfirmed==null) customerStartConfirmed=false;
        if(providerStartConfirmed==null) providerStartConfirmed=false;
        if(advanceReleased==null) advanceReleased=false;
        if(status==null) status=EscrowStatus.HELD;
    }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
