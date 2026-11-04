package com.skillrental.repository;

import com.skillrental.entity.NotificationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from NotificationCampaign c where c.id = :id")
    Optional<NotificationCampaign> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<NotificationCampaign> findByRequestKey(String requestKey);
    List<NotificationCampaign> findTop50ByOrderByCreatedAtDescIdDesc();
}
