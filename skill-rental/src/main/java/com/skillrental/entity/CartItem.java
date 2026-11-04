package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="cart_items", uniqueConstraints=@UniqueConstraint(name="uq_cart_item_service", columnNames={"cart_id","service_id"}), indexes=@Index(name="idx_cart_items_service", columnList="service_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CartItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="cart_id", nullable=false) private Cart cart;
    @ManyToOne(optional=false) @JoinColumn(name="service_id", nullable=false) private ServiceCatalog service;
    @Column(nullable=false) @Builder.Default private Integer quantity = 1;
    @Column(name="unit_price_snapshot", nullable=false, precision=12, scale=2) private BigDecimal unitPriceSnapshot;
    @Column(name="added_at") private LocalDateTime addedAt;
    @PrePersist void prePersist() { if(addedAt==null) addedAt=LocalDateTime.now(); if(quantity==null) quantity=1; }
}
