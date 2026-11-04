package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.*;

@Entity @Table(name="provider_profiles") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) @JoinColumn(name="user_id",unique=true) private User user;
    @Column(nullable=false) private String headline;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal hourlyRate;
    @Column(nullable=false) private Double rating;
    @Column(nullable=false) private Integer reviewCount;
    @Column(nullable=false) private Boolean premium;
    @Column(length=1200) private String introduction;
    private String availability;
    @Column(name="is_approved", nullable=false) @Builder.Default private Boolean approved = false;
    @Column(name="profile_status", nullable=false, length=20) @Builder.Default private String profileStatus = "ACTIVE";
    @Column(name="created_at") private java.time.LocalDateTime createdAt;
    @Column(name="updated_at") private java.time.LocalDateTime updatedAt;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="provider_skills",joinColumns=@JoinColumn(name="provider_profile_id"),inverseJoinColumns=@JoinColumn(name="skill_id"))
    @Builder.Default private Set<Skill> skills=new LinkedHashSet<>();
    @PrePersist void prePersist() { if(approved==null) approved=false; if(profileStatus==null) profileStatus="ACTIVE"; if(createdAt==null) createdAt=java.time.LocalDateTime.now(); if(updatedAt==null) updatedAt=java.time.LocalDateTime.now(); }
    @PreUpdate void preUpdate() { updatedAt=java.time.LocalDateTime.now(); }
}
