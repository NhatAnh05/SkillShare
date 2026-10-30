package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="bookings", uniqueConstraints=
        @UniqueConstraint(name="uq_bookings_idempotency_key", columnNames="idempotency_key"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private User customer;
    @ManyToOne(optional=false) private User provider;
    @ManyToOne(optional=true) @JoinColumn(name="service_id") private ServiceCatalog service;
    @Column(name="service_name", nullable=false, length=255) private String serviceName;
    @Column(nullable=false) private LocalDate bookingDate;
    @Column(nullable=false) private LocalTime timeSlot;
    @Column(length=1200) private String note;
    @Column(nullable=false) private Boolean premium;
    @Column(nullable=false) private Boolean directBooking;
    @Column(name="duration_hours", nullable=false, columnDefinition="INT DEFAULT 2")
    @Builder.Default private Integer durationHours = 2;
    @Column(name="idempotency_key", length=36) private String idempotencyKey;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private BookingStatus status;
    @Column(name="cancellation_reason", length=1000) private String cancellationReason;
    @ManyToOne @JoinColumn(name="cancelled_by_user_id") private User cancelledBy;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=LocalDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
