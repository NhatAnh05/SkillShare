package com.skillrental.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Table(name="support_notes") @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupportNote {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private Conversation conversation;
 @ManyToOne(optional=false) private User admin;
 @Column(nullable=false,length=2000) private String content;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void stamp(){createdAt=LocalDateTime.now();}
}
