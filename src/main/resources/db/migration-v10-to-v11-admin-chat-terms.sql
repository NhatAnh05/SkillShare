-- Apply after migration-v9-to-v10-notifications-support-deliveries.sql.
-- Back up the database before applying this migration.

CREATE TABLE support_assignment_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    changed_by_id BIGINT NOT NULL,
    previous_admin_id BIGINT NULL,
    assigned_admin_id BIGINT NULL,
    action VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_support_assignment_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_support_assignment_changed_by FOREIGN KEY (changed_by_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_support_assignment_previous_admin FOREIGN KEY (previous_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_support_assignment_admin FOREIGN KEY (assigned_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_support_assignment_conversation (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE terms_acceptances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    policy_type VARCHAR(40) NOT NULL,
    policy_version VARCHAR(40) NOT NULL,
    accepted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    source VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_terms_acceptance_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
    INDEX idx_terms_acceptance_user_time (user_id, accepted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
