package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="skills") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Skill {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=80) private String name;
    @Column(length=500) private String description;
    @Column(name="created_at") private java.time.LocalDateTime createdAt;
    @PrePersist void prePersist() { if(createdAt==null) createdAt=java.time.LocalDateTime.now(); }
}
