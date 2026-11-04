package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "delivery_submissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_delivery_order_version", columnNames = {"order_id", "version"}),
        indexes = @Index(name = "idx_delivery_order_sent", columnList = "order_id,sent_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliverySubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RentalOrder order;
    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;
    @Column(nullable = false)
    private Integer version;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(name = "usage_notes", length = 4000)
    private String usageNotes;
    @Column(nullable = false, length = 24)
    @Builder.Default
    private String status = "SUBMITTED";
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryLink> links = new ArrayList<>();
    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryFile> files = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
