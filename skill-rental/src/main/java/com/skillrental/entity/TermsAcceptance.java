package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "terms_acceptances", indexes = {
        @Index(name = "idx_terms_acceptance_user_time", columnList = "user_id, accepted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsAcceptance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "policy_type", nullable = false, length = 40)
    private String policyType;

    @Column(name = "policy_version", nullable = false, length = 40)
    private String policyVersion;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @PrePersist
    void prePersist() {
        if (acceptedAt == null) acceptedAt = LocalDateTime.now();
    }
}
