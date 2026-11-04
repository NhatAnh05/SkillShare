package com.skillrental.repository;

import com.skillrental.entity.SupportAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportAssignmentHistoryRepository extends JpaRepository<SupportAssignmentHistory, Long> {
    long countByChangedByAndCreatedAtAfter(com.skillrental.entity.User admin,java.time.LocalDateTime after);
    List<SupportAssignmentHistory> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
