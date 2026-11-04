package com.skillrental.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Table(name="conversation_access_audits") @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationAccessAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private ConversationReviewGrant grant;
 @ManyToOne(optional=false) private User actor;
 @Column(nullable=false,length=32) private String action;
 @Column(nullable=false,length=2000) private String reason;
 @Column(name="data_scope",nullable=false,length=500) private String dataScope;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void stamp(){createdAt=LocalDateTime.now();}
}
