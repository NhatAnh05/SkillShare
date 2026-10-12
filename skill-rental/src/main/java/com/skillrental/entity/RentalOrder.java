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
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private OrderStatus status;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="cancellation_reason", length=1000) private String cancellationReason;
    @ManyToOne @JoinColumn(name="cancelled_by_user_id") private User cancelledBy;
    @OneToMany(mappedBy="order", cascade=CascadeType.ALL, orphanRemoval=true)
    @Builder.Default private List<RentalOrderItem> items = new ArrayList<>();
}
