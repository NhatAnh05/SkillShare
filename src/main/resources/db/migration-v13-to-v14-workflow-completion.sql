-- MySQL 8: apply once after v12 -> v13. Back up database and private file storage first.
ALTER TABLE conversations ADD COLUMN support_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 ADD COLUMN linked_complaint_id BIGINT NULL,
 ADD CONSTRAINT fk_conversation_linked_complaint FOREIGN KEY (linked_complaint_id) REFERENCES complaints(id);
ALTER TABLE messages ADD COLUMN client_key VARCHAR(36) NULL,
 ADD CONSTRAINT uq_message_sender_client UNIQUE (sender_id,client_key);
CREATE TABLE support_notes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, conversation_id BIGINT NOT NULL, admin_id BIGINT NOT NULL,
 content VARCHAR(2000) NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY (conversation_id) REFERENCES conversations(id), FOREIGN KEY (admin_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE work_documents (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,order_id BIGINT NULL,conversation_id BIGINT NULL,sender_id BIGINT NOT NULL,
 description VARCHAR(2000) NOT NULL,original_name VARCHAR(255) NULL,storage_key VARCHAR(36) NULL,file_size BIGINT NULL,
 content_type VARCHAR(100) NULL,url VARCHAR(1000) NULL,created_at DATETIME(6) NOT NULL,
 INDEX idx_work_document_order (order_id,id),INDEX idx_work_document_conversation (conversation_id,id),
 FOREIGN KEY (order_id) REFERENCES rental_orders(id),FOREIGN KEY (conversation_id) REFERENCES conversations(id),FOREIGN KEY (sender_id) REFERENCES users(id),
 CONSTRAINT ck_work_document_target CHECK ((order_id IS NOT NULL AND conversation_id IS NULL) OR (order_id IS NULL AND conversation_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE conversation_review_grants (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,conversation_id BIGINT NOT NULL,complaint_id BIGINT NOT NULL,
 requester_id BIGINT NOT NULL,approver_id BIGINT NULL,reason VARCHAR(2000) NOT NULL,status VARCHAR(16) NOT NULL,
 from_time DATETIME(6) NOT NULL,to_time DATETIME(6) NOT NULL,expires_at DATETIME(6) NULL,export_allowed BOOLEAN NOT NULL DEFAULT FALSE,created_at DATETIME(6) NOT NULL,
 FOREIGN KEY (conversation_id) REFERENCES conversations(id),FOREIGN KEY (complaint_id) REFERENCES complaints(id),FOREIGN KEY (requester_id) REFERENCES users(id),FOREIGN KEY (approver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE conversation_access_audits (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,grant_id BIGINT NOT NULL,actor_id BIGINT NOT NULL,action VARCHAR(32) NOT NULL,
 reason VARCHAR(2000) NOT NULL,data_scope VARCHAR(500) NOT NULL,created_at DATETIME(6) NOT NULL,
 FOREIGN KEY (grant_id) REFERENCES conversation_review_grants(id),FOREIGN KEY (actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE policy_snapshots (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,policy_type VARCHAR(40) NOT NULL,policy_version VARCHAR(40) NOT NULL,
 title VARCHAR(160) NOT NULL,content TEXT NOT NULL,CONSTRAINT uq_policy_snapshot UNIQUE(policy_type,policy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Snapshot content for current versions is initialized from the actual policy text at startup.
-- Existing historical acceptances remain unchanged. Do not backfill old policy content with new text.
