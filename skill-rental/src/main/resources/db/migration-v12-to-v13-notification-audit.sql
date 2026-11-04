-- Apply once after migration-v11-to-v12-admin-notification-campaigns.sql (MySQL).
ALTER TABLE notification_campaigns MODIFY request_key VARCHAR(36) NOT NULL;

CREATE TABLE notification_campaign_audits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    recipient_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_campaign_audit_campaign FOREIGN KEY (campaign_id) REFERENCES notification_campaigns(id),
    CONSTRAINT fk_campaign_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
