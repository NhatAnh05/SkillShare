package com.skillrental.repository;
import com.skillrental.entity.NotificationCampaignAudit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationCampaignAuditRepository extends JpaRepository<NotificationCampaignAudit, Long> {
    java.util.List<NotificationCampaignAudit> findByCampaignIdOrderByIdAsc(Long campaignId);
}
