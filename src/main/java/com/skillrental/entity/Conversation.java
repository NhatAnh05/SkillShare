package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="conversations", uniqueConstraints={
        @UniqueConstraint(name="uq_conversation_customer_provider", columnNames={"customer_id","provider_id"}),
        @UniqueConstraint(name="uq_conversation_support_key", columnNames={"support_key"})
}, indexes={@Index(name="idx_conversations_provider", columnList="provider_id"),
        @Index(name="idx_conversations_activity", columnList="last_message_at")}) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="customer_id") private User customer;
    @ManyToOne @JoinColumn(name="provider_id") private User provider;
    @ManyToOne @JoinColumn(name="support_admin_id") private User supportAdmin;
    @Column(name="support_conversation", nullable=false, columnDefinition="BOOLEAN DEFAULT FALSE") @Builder.Default private Boolean supportConversation = false;
    @Column(name="support_key", length=80) private String supportKey;
    @ManyToOne @JoinColumn(name="order_id") private RentalOrder order;
    @Column(name="support_status",nullable=false,length=16,columnDefinition="VARCHAR(16) DEFAULT 'OPEN'") @Builder.Default private String supportStatus="OPEN";
    @ManyToOne @JoinColumn(name="linked_complaint_id") private Complaint linkedComplaint;
    @Column(name="created_at") private java.time.LocalDateTime createdAt;
    @Column(name="last_message_at") private java.time.LocalDateTime lastMessageAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=java.time.LocalDateTime.now(); if(lastMessageAt==null)lastMessageAt=createdAt; }
}
