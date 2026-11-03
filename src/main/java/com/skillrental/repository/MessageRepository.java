package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface MessageRepository extends JpaRepository<Message,Long> {
    java.util.List<Message> findTop200ByConversationAndSentAtBetweenAndIdGreaterThanOrderByIdAsc(Conversation conversation,LocalDateTime from,LocalDateTime to,Long after);
    Optional<Message> findBySenderAndClientKey(User sender, String clientKey);
    long countBySenderAndSentAtAfter(User sender, LocalDateTime after);
    List<Message> findTop50ByConversationAndIdLessThanOrderByIdDesc(Conversation c, Long beforeId);
    List<Message> findByConversationOrderBySentAtAsc(Conversation c);
    Optional<Message> findTopByConversationOrderBySentAtDesc(Conversation conversation);

    @Query("select count(m) from Message m where m.conversation = :conversation "
            + "and m.sender.id <> :readerId and m.readAt is null")
    long countUnreadByConversationAndReader(@Param("conversation") Conversation conversation,
                                            @Param("readerId") Long readerId);

    @Modifying
    @Query("update Message m set m.readAt = :readAt where m.conversation = :conversation "
            + "and m.sender.id <> :readerId and m.readAt is null")
    int markUnreadMessagesRead(@Param("conversation") Conversation conversation,
                               @Param("readerId") Long readerId,
                               @Param("readAt") LocalDateTime readAt);
}
