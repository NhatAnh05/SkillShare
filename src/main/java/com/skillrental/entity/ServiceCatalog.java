package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name="services", indexes={
    @Index(name="idx_services_provider_status", columnList="provider_id,status"),
    @Index(name="idx_services_status_price", columnList="status,price"),
    @Index(name="idx_services_category_status", columnList="category,status"),
    @Index(name="idx_services_rating", columnList="rating")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceCatalog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="provider_id", nullable=false) private User provider;
    @Column(nullable=false, length=160) private String name;
    @Column(length=80) private String category;
    @Column(nullable=false, length=2500) private String description;
    @Enumerated(EnumType.STRING) @Column(name="pricing_type", nullable=false, length=20)
    @Builder.Default private ServicePricingType pricingType = ServicePricingType.FIXED;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal price;
    @Column(name="duration_minutes") private Integer durationMinutes;
    @Column(name="cover_image_url", length=500) private String coverImageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    @Builder.Default private ServiceStatus status = ServiceStatus.DRAFT;
    @Column(nullable=false) @Builder.Default private Double rating = 0.0;
    @Column(name="review_count", nullable=false) @Builder.Default private Integer reviewCount = 0;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @Column(name="published_at") private LocalDateTime publishedAt;
    @ManyToMany
    @JoinTable(name="service_skills", joinColumns=@JoinColumn(name="service_id"), inverseJoinColumns=@JoinColumn(name="skill_id"))
    @Builder.Default private Set<Skill> skills = new LinkedHashSet<>();
    @PrePersist void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (pricingType == null) pricingType = ServicePricingType.FIXED;
        if (status == null) status = ServiceStatus.DRAFT;
        if (rating == null) rating = 0.0;
        if (reviewCount == null) reviewCount = 0;
    }
    @PreUpdate void preUpdate() { updatedAt = LocalDateTime.now(); }
}
