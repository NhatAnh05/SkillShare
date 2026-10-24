package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="customer_profiles") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerProfile {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(optional=false) @JoinColumn(name="user_id",unique=true) private User user;
    private String location;
    private String preferredSkills;
}
