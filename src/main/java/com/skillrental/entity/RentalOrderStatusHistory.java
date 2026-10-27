package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="rental_order_status_history", indexes=@Index(name="idx_order_history_order_date", columnList="order_id,created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RentalOrderStatusHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="order_id", nullable=false) private RentalOrder order;
    @Column(name="previous_status", length=30) private String previousStatus;
    @Column(name="new_status", nullable=false, length=50) private String newStatus;
    @ManyToOne @JoinColumn(name="changed_by_user_id") private User changedBy;
    @Column(length=1000) private String note;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=LocalDateTime.now(); }
}
