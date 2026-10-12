package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="conversations", uniqueConstraints=@UniqueConstraint(name="uq_conversation_customer_provider", columnNames={"customer_id","provider_id"}), indexes=@Index(name="idx_conversations_provider", columnList="provider_id")) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="customer_id", nullable=false) private User customer;
    @ManyToOne(optional=false) @JoinColumn(name="provider_id", nullable=false) private User provider;
    @Column(name="created_at") private java.time.LocalDateTime createdAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=java.time.LocalDateTime.now(); }
}
