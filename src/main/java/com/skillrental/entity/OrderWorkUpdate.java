package com.skillrental.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="order_work_updates", indexes=@Index(name="idx_work_update_order",columnList="order_id,id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderWorkUpdate {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="order_id") private RentalOrder order;
 @ManyToOne(optional=false) @JoinColumn(name="provider_id") private User provider;
 @Column(nullable=false,length=2000) private String description;
 @Column(name="progress_percent",nullable=false) private Integer progressPercent;
 @Column(name="estimated_completion_at",nullable=false) private LocalDateTime estimatedCompletionAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void created(){if(createdAt==null)createdAt=LocalDateTime.now();}
}
