package com.skillrental.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Table(name="policy_snapshots",uniqueConstraints=@UniqueConstraint(name="uq_policy_snapshot",columnNames={"policy_type","policy_version"}))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicySnapshot {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="policy_type",nullable=false,length=40) private String policyType;
 @Column(name="policy_version",nullable=false,length=40) private String policyVersion;
 @Column(nullable=false,length=160) private String title;
 @Column(nullable=false,columnDefinition="TEXT") private String content;
}
