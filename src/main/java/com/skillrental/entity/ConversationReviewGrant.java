package com.skillrental.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Table(name="conversation_review_grants") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationReviewGrant {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private Conversation conversation;
 @ManyToOne(optional=false) private Complaint complaint;
 @ManyToOne(optional=false) private User requester;
 @ManyToOne private User approver;
 @Column(nullable=false,length=2000) private String reason;
 @Column(nullable=false,length=16) @Builder.Default private String status="PENDING";
 @Column(name="from_time",nullable=false) private LocalDateTime fromTime;
 @Column(name="to_time",nullable=false) private LocalDateTime toTime;
 @Column(name="expires_at") private LocalDateTime expiresAt;
 @Column(name="export_allowed",nullable=false) @Builder.Default private boolean exportAllowed=false;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void stamp(){createdAt=LocalDateTime.now();}
}
