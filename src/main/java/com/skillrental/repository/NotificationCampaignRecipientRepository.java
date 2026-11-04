package com.skillrental.repository;

import com.skillrental.entity.NotificationCampaignRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface NotificationCampaignRecipientRepository extends JpaRepository<NotificationCampaignRecipient, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NotificationCampaignRecipient r where r.id = :id")
    Optional<NotificationCampaignRecipient> findByIdForUpdate(@Param("id") Long id);
    List<NotificationCampaignRecipient> findTop100ByCampaignIdAndDeliveryStatusAndIdGreaterThanOrderByIdAsc(Long campaignId, String status, Long afterId);
    List<NotificationCampaignRecipient> findByCampaignIdOrderByIdAsc(Long campaignId);
    Page<NotificationCampaignRecipient> findByCampaignIdOrderByIdAsc(Long campaignId, Pageable pageable);
    List<NotificationCampaignRecipient> findByCampaignIdAndDeliveryStatusOrderByIdAsc(Long campaignId, String status);
    List<NotificationCampaignRecipient> findTop100ByCampaignIdAndDeliveryStatusOrderByIdAsc(Long campaignId, String status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationCampaignRecipient> findByNotificationId(Long notificationId);

    @Query("select r.id from NotificationCampaignRecipient r where r.campaign.id = :campaignId and r.deliveryStatus in ('FAILED','QUEUED','WS_FAILED') order by r.id")
    List<Long> findRetryableIds(@Param("campaignId") Long campaignId);
    long countByCampaignIdAndDeliveryStatus(Long campaignId, String status);
    long countByCampaignId(Long campaignId);
    long countByCampaignIdAndNotificationIsNotNull(Long campaignId);

    @Query("select count(r) from NotificationCampaignRecipient r where r.campaign.id = :campaignId and r.notification.readAt is not null")
    long countReadByCampaignId(@Param("campaignId") Long campaignId);

    @Query("select count(r) from NotificationCampaignRecipient r where r.campaign.id = :campaignId and r.notification is not null and r.notification.readAt is null")
    long countUnreadByCampaignId(@Param("campaignId") Long campaignId);

    @Query("select count(r) from NotificationCampaignRecipient r where r.campaign.id = :campaignId and r.deliveryStatus in ('FAILED','WS_FAILED')")
    long countFailuresByCampaignId(@Param("campaignId") Long campaignId);

    @Query("select count(r) from NotificationCampaignRecipient r where r.campaign.id = :campaignId and r.deliveryStatus = 'QUEUED'")
    long countQueuedByCampaignId(@Param("campaignId") Long campaignId);
}
