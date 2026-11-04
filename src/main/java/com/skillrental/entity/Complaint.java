package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="complaints") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=true) @JoinColumn(name="customer_id") private User customer;
    @ManyToOne(optional=true) @JoinColumn(name="reporter_id") private User reporter;
    @ManyToOne(optional=true) @JoinColumn(name="reported_user_id") private User reportedUser;
    @ManyToOne(optional=true) @JoinColumn(name="handled_by_admin_id") private User handledBy;
    @ManyToOne @JoinColumn(name="order_id") private RentalOrder order;
    @Enumerated(EnumType.STRING) @Column(name="complaint_type", nullable=false, length=30) @Builder.Default private ComplaintType complaintType = ComplaintType.ORDER;
    @Column(nullable=false) private String subject;
    @Column(nullable=false,length=2500) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private ComplaintStatus status;
    @Column(name="resolution_notes", length=1500) private String resolutionNotes;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="resolved_at") private LocalDateTime resolvedAt;
}
