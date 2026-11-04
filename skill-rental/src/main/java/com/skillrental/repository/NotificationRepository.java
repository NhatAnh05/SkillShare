package com.skillrental.repository;

import com.skillrental.entity.Notification;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByRecipientOrderByCreatedAtDescIdDesc(User recipient);
    org.springframework.data.domain.Page<Notification> findByRecipientOrderByCreatedAtDescIdDesc(
            User recipient, org.springframework.data.domain.Pageable pageable);
    long countByRecipientAndReadAtIsNull(User recipient);
    Optional<Notification> findByIdAndRecipient(Long id, User recipient);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.recipient = :recipient and n.readAt is null")
    int markAllRead(@Param("recipient") User recipient, @Param("readAt") LocalDateTime readAt);
}
