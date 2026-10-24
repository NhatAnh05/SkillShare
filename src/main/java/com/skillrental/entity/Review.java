package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="reviews") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) private RentalOrder order;
    @ManyToOne(optional=true) @JoinColumn(name="service_id") private ServiceCatalog service;
    @ManyToOne(optional=false) private User customer;
    @ManyToOne(optional=false) private User provider;
    @Column(nullable=false) private Integer rating;
    @Column(nullable=false,length=2000) private String comment;
    @Column(length=2000) private String providerResponse;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=LocalDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
