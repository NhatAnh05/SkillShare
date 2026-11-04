package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="carts", indexes=@Index(name="idx_carts_customer_status", columnList="customer_id,status"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cart {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="customer_id", nullable=false) private User customer;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) @Builder.Default private CartStatus status = CartStatus.ACTIVE;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @Column(name="checked_out_at") private LocalDateTime checkedOutAt;
    @OneToMany(mappedBy="cart", cascade=CascadeType.ALL, orphanRemoval=true)
    @Builder.Default private List<CartItem> items = new ArrayList<>();
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=LocalDateTime.now(); if(status==null) status=CartStatus.ACTIVE; }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
