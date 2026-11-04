package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="applies", uniqueConstraints=@UniqueConstraint(name="uq_applies_request_provider", columnNames={"request_id","provider_id"}), indexes={@Index(name="idx_applies_request_date", columnList="request_id,applied_at"), @Index(name="idx_applies_provider_status", columnList="provider_id,status")}) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Apply {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="request_id") private ServiceRequest request;
    @ManyToOne(optional=false) @JoinColumn(name="provider_id") private User provider;
    @Column(nullable=false,length=1800) private String proposal;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal proposedPrice;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private ApplyStatus status;
    @Column(name="applied_at") private LocalDateTime appliedAt;
    @PrePersist void prePersist() { if(appliedAt==null) appliedAt=LocalDateTime.now(); }
}
