package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity @Table(name="service_requests") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="customer_id") private User customer;
    @Column(nullable=false) private String title;
    @Column(nullable=false) private String requiredSkills;
    @Column(nullable=false,length=2500) private String description;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal budget;
    @Column(nullable=false) private LocalDate deadline;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private RequestStatus status;
    @ManyToOne @JoinColumn(name="selected_provider_id") private User selectedProvider;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @ManyToMany
    @JoinTable(name="service_request_skills", joinColumns=@JoinColumn(name="request_id"), inverseJoinColumns=@JoinColumn(name="skill_id"))
    @Builder.Default private Set<Skill> skills = new LinkedHashSet<>();
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); if(updatedAt==null) updatedAt=LocalDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt=LocalDateTime.now(); }
}
