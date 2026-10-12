package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="oauth_identities",
    uniqueConstraints={
        @UniqueConstraint(name="uq_oauth_provider_subject", columnNames={"identity_provider","provider_subject"}),
        @UniqueConstraint(name="uq_oauth_user_provider", columnNames={"user_id","identity_provider"})
    }, indexes=@Index(name="idx_oauth_email", columnList="email_at_provider"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthIdentity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="user_id", nullable=false) private User user;
    @Column(name="identity_provider", nullable=false, length=30) private String identityProvider;
    @Column(name="provider_subject", nullable=false, length=255) private String providerSubject;
    @Column(name="email_at_provider", length=160) private String emailAtProvider;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); }
}
