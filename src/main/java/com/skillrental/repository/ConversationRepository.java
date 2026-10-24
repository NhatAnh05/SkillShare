package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ConversationRepository extends JpaRepository<Conversation,Long> {
    List<Conversation> findByCustomer(User customer);
    Optional<Conversation> findByCustomerAndProvider(User customer,User provider);
}
