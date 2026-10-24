package com.skillrental.service;

import com.skillrental.entity.*;
import com.skillrental.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor public class ChatService {
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    public List<Conversation> list(User u) {
        return conversations.findByCustomer(u);
    }
    public Conversation owned(Long id,User u) {
        var c=conversations.findById(id).orElseThrow();
        if(!c.getCustomer().getId().equals(u.getId()))throw new IllegalArgumentException("Không có quyền");
        return c;
    }
    public List<Message> messages(Conversation c) {
        return messages.findByConversationOrderBySentAtAsc(c);
    }
    @Transactional public void send(Conversation c,User u,String text) {
        messages.save(Message.builder().conversation(c).sender(u).content(text).sentAt(LocalDateTime.now()).build());
    }
}
