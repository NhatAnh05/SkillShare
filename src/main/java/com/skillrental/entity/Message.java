package com.skillrental.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="messages", uniqueConstraints=@UniqueConstraint(name="uq_message_sender_client", columnNames={"sender_id","client_key"})) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {
    @Column(name="client_key", length=36) private String clientKey;
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private Conversation conversation;
    @ManyToOne(optional=false) private User sender;
    @Column(nullable=false,length=2500) private String content;
    @Column(name="sent_at") private LocalDateTime sentAt;
    @Column(name="read_at") private LocalDateTime readAt;
    @PrePersist void prePersist() { if(sentAt==null) sentAt=LocalDateTime.now(); }
}
