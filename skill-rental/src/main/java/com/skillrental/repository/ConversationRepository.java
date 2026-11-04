package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ConversationRepository extends JpaRepository<Conversation,Long> {
    @Query("select c from Conversation c where c.customer = :user or c.provider = :user "
            + "or (c.supportConversation = true and :admin = true "
            + "and (c.supportAdmin is null or c.supportAdmin = :user)) "
            + "order by c.lastMessageAt desc, c.id desc")
    List<Conversation> findAccessibleTo(@Param("user") User user, @Param("admin") boolean admin);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") Long id);
    Optional<Conversation> findByCustomerAndProvider(User customer,User provider);
    Optional<Conversation> findBySupportKey(String supportKey);
    List<Conversation> findBySupportConversationTrueOrderByLastMessageAtDesc();
}
