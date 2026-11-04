package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "delivery_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private DeliverySubmission delivery;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "storage_key", nullable = false, unique = true, length = 80)
    private String storageKey;
    @Column(name = "content_type", nullable = false, length = 160)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
}
