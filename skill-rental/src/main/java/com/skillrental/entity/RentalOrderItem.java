package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="rental_order_items", uniqueConstraints=@UniqueConstraint(name="uq_order_item_booking", columnNames="booking_id"), indexes={
    @Index(name="idx_order_items_order", columnList="order_id"),
    @Index(name="idx_order_items_service", columnList="service_id"),
    @Index(name="idx_order_items_provider", columnList="provider_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RentalOrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="order_id", nullable=false) private RentalOrder order;
    @ManyToOne(optional=true) @JoinColumn(name="service_id") private ServiceCatalog service;
    @ManyToOne(optional=false) @JoinColumn(name="provider_id", nullable=false) private User provider;
    @ManyToOne(optional=true) @JoinColumn(name="booking_id") private Booking booking;
    @Column(name="service_name_snapshot", nullable=false, length=255) private String serviceNameSnapshot;
    @Column(nullable=false) @Builder.Default private Integer quantity = 1;
    @Column(name="unit_price", nullable=false, precision=12, scale=2) private BigDecimal unitPrice;
    @Column(name="line_total", nullable=false, precision=12, scale=2) private BigDecimal lineTotal;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); if(quantity==null) quantity=1; }
}
