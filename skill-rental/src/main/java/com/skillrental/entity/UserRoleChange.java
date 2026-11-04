package com.skillrental.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="user_role_changes",indexes=@Index(name="idx_role_change_time",columnList="changed_at,id"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserRoleChange {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) @JoinColumn(name="target_user_id") private User target;
 @ManyToOne(optional=false) @JoinColumn(name="actor_user_id") private User actor;
 @Enumerated(EnumType.STRING) @Column(name="previous_role",nullable=false,length=20) private Role previousRole;
 @Enumerated(EnumType.STRING) @Column(name="new_role",nullable=false,length=20) private Role newRole;
 @Column(nullable=false,length=500) private String reason;
 @Column(name="changed_at",nullable=false) private LocalDateTime changedAt;
 @PrePersist void created(){if(changedAt==null)changedAt=LocalDateTime.now();}
}
