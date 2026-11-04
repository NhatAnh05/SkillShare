package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "delivery_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private DeliverySubmission delivery;
    @Column(nullable = false, length = 1000)
    private String url;
    @Column(length = 160)
    private String label;
}
