-- Apply after migration-v10-to-v11-admin-chat-terms.sql.
-- Back up the database before applying this migration.

ALTER TABLE notifications
    ADD COLUMN sender_id BIGINT NULL AFTER recipient_id,
    ADD CONSTRAINT fk_notifications_sender FOREIGN KEY (sender_id)
        REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL;

CREATE TABLE notification_campaigns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    request_key CHAR(36) NOT NULL,
    title VARCHAR(180) NOT NULL,
    content VARCHAR(1200) NOT NULL,
    type VARCHAR(40) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    target_url VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_campaign_request UNIQUE (request_key),
    CONSTRAINT fk_notification_campaign_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_notification_campaign_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_campaign_recipients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    notification_id BIGINT NULL,
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    websocket_attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_campaign_recipient UNIQUE (campaign_id, recipient_id),
    CONSTRAINT uq_notification_campaign_notification UNIQUE (notification_id),
    CONSTRAINT fk_notification_campaign_recipient_campaign FOREIGN KEY (campaign_id) REFERENCES notification_campaigns(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_notification_campaign_recipient_user FOREIGN KEY (recipient_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_notification_campaign_recipient_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_notification_campaign_recipient_status (campaign_id, delivery_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
