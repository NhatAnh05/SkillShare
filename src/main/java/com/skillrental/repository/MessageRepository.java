package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MessageRepository extends JpaRepository<Message,Long> {
    List<Message> findByConversationOrderBySentAtAsc(Conversation c);
}
