package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="messages") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private Conversation conversation;
    @ManyToOne(optional=false) private User sender;
    @Column(nullable=false,length=2500) private String content;
    @Column(name="sent_at") private LocalDateTime sentAt;
    @PrePersist void prePersist() { if(sentAt==null) sentAt=LocalDateTime.now(); }
}
