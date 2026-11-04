-- Apply after migration-v8-to-v9-chat-read-status.sql.
-- Back up the database before applying this migration.

ALTER TABLE conversations
    DROP CHECK ck_conversations_different_users;

ALTER TABLE conversations
    MODIFY COLUMN customer_id BIGINT NULL,
    MODIFY COLUMN provider_id BIGINT NULL,
    ADD COLUMN support_admin_id BIGINT NULL,
    ADD COLUMN support_conversation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN support_key VARCHAR(80) NULL,
    ADD COLUMN order_id BIGINT NULL,
    ADD COLUMN last_message_at DATETIME(6) NULL,
    ADD CONSTRAINT uq_conversation_support_key UNIQUE (support_key),
    ADD CONSTRAINT fk_conversations_support_admin FOREIGN KEY (support_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conversations_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE SET NULL,
    ADD INDEX idx_conversations_activity (last_message_at);

UPDATE conversations c
LEFT JOIN (
    SELECT conversation_id, MAX(sent_at) AS latest_message_at
    FROM messages
    GROUP BY conversation_id
) m ON m.conversation_id = c.id
SET c.last_message_at = COALESCE(m.latest_message_at, c.created_at);

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    content VARCHAR(1200) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id BIGINT NULL,
    target_url VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX idx_notifications_recipient_created (recipient_id, created_at),
    INDEX idx_notifications_recipient_read (recipient_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    version INT NOT NULL,
    description VARCHAR(4000) NOT NULL,
    usage_notes VARCHAR(4000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_delivery_order_version UNIQUE (order_id, version),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_delivery_order_sent (order_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id BIGINT NOT NULL,
    url VARCHAR(1000) NOT NULL,
    label VARCHAR(160) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_delivery_link_submission FOREIGN KEY (delivery_id) REFERENCES delivery_submissions(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    content_type VARCHAR(160) NOT NULL,
    file_size BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_delivery_file_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_delivery_file_submission FOREIGN KEY (delivery_id) REFERENCES delivery_submissions(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_revision_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_delivery_revision_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_revision_submission FOREIGN KEY (delivery_id) REFERENCES delivery_submissions(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_revision_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_delivery_revision_order (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
