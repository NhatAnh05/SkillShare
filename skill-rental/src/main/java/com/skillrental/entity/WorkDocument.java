package com.skillrental.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Table(name="work_documents",indexes={@Index(name="idx_work_document_order",columnList="order_id,id"),@Index(name="idx_work_document_conversation",columnList="conversation_id,id")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne @JoinColumn(name="order_id") private RentalOrder order;
 @ManyToOne @JoinColumn(name="conversation_id") private Conversation conversation;
 @ManyToOne(optional=false) private User sender;
 @Column(nullable=false,length=2000) private String description;
 @Column(name="original_name",length=255) private String originalName;
 @Column(name="storage_key",length=36) private String storageKey;
 @Column(name="file_size") private Long fileSize;
 @Column(name="content_type",length=100) private String contentType;
 @Column(length=1000) private String url;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void stamp(){createdAt=LocalDateTime.now();}
}
