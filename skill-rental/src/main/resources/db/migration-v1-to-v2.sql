-- Upgrade from the earlier SkillShare_Database_Design schema (v1) to the canonical v2 schema.
-- Take a backup first. This script is intended to be run ONCE against the v1 database.
-- MySQL DDL auto-commits; do not treat the transaction as a rollback boundary.
USE skill_rental;

-- Existing accounts and provider profiles remain usable after migration.
ALTER TABLE users
  ADD COLUMN email_verified BIT(1) NOT NULL DEFAULT b'0' AFTER bio,
  ADD COLUMN email_verification_token VARCHAR(120) NULL AFTER email_verified,
  ADD COLUMN email_verification_expires_at DATETIME(6) NULL AFTER email_verification_token,
  ADD COLUMN reset_token_expires_at DATETIME(6) NULL AFTER reset_token,
  ADD COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' AFTER reset_token_expires_at,
  ADD COLUMN last_login_at DATETIME(6) NULL AFTER account_status,
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
  ADD CONSTRAINT uq_users_email_verification_token UNIQUE (email_verification_token),
  ADD CONSTRAINT ck_users_account_status CHECK (account_status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','LOCKED','DELETED'));
UPDATE users SET email_verified=b'1', account_status='ACTIVE';
ALTER TABLE users MODIFY COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE provider_profiles
  ADD COLUMN is_approved BIT(1) NOT NULL DEFAULT b'0',
  ADD COLUMN profile_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT ck_provider_profile_status CHECK (profile_status IN ('ACTIVE','INACTIVE','SUSPENDED'));
UPDATE provider_profiles SET is_approved=b'1', profile_status='ACTIVE';

ALTER TABLE skills
  ADD COLUMN description VARCHAR(500) NULL,
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE service_requests
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE applies
  ADD CONSTRAINT uq_applies_request_provider UNIQUE (request_id, provider_id);
UPDATE applies SET applied_at=CURRENT_TIMESTAMP(6) WHERE applied_at IS NULL;
ALTER TABLE applies MODIFY COLUMN applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE bookings
  ADD COLUMN service_id BIGINT NULL AFTER provider_id,
  ADD COLUMN cancellation_reason VARCHAR(1000) NULL,
  ADD COLUMN cancelled_by_user_id BIGINT NULL,
  ADD COLUMN cancelled_at DATETIME(6) NULL,
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT fk_bookings_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  ADD INDEX idx_bookings_service (service_id);

ALTER TABLE rental_orders
  MODIFY COLUMN booking_id BIGINT NULL,
  MODIFY COLUMN provider_id BIGINT NULL,
  ADD COLUMN cancelled_at DATETIME(6) NULL,
  ADD COLUMN cancellation_reason VARCHAR(1000) NULL,
  ADD COLUMN cancelled_by_user_id BIGINT NULL,
  ADD CONSTRAINT fk_rental_orders_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE payments
  ADD COLUMN gateway_name VARCHAR(40) NULL AFTER method,
  ADD COLUMN gateway_transaction_id VARCHAR(180) NULL,
  ADD COLUMN idempotency_key VARCHAR(120) NULL,
  ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'VND',
  ADD COLUMN failure_reason VARCHAR(1000) NULL,
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD CONSTRAINT uq_payment_gateway_transaction UNIQUE (gateway_name, gateway_transaction_id),
  ADD CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key);
ALTER TABLE payments DROP CHECK ck_payments_status;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status CHECK (status IN ('PENDING','PAID','FAILED','PARTIALLY_REFUNDED','REFUNDED'));

ALTER TABLE reviews
  ADD COLUMN service_id BIGINT NULL AFTER order_id,
  ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  ADD INDEX idx_reviews_service_created (service_id, created_at);

-- CHECK constraints cannot reference columns participating in cascading FK actions in MySQL.
-- User IDs are immutable, so use ON UPDATE RESTRICT for both reporter relationships.
-- MySQL does not allow explicit ON UPDATE/ON DELETE referential-action clauses
-- on columns referenced by a CHECK constraint. Omit those clauses for customer_id
-- and reporter_id; InnoDB defaults to NO ACTION (RESTRICT-equivalent), preserving FK integrity.
ALTER TABLE complaints
  DROP FOREIGN KEY fk_complaints_customer,
  MODIFY COLUMN customer_id BIGINT NULL,
  ADD COLUMN reporter_id BIGINT NULL,
  ADD COLUMN reported_user_id BIGINT NULL,
  ADD COLUMN handled_by_admin_id BIGINT NULL,
  ADD COLUMN complaint_type VARCHAR(30) NOT NULL DEFAULT 'ORDER',
  ADD COLUMN resolution_notes VARCHAR(1500) NULL,
  ADD COLUMN resolved_at DATETIME(6) NULL,
  ADD CONSTRAINT fk_complaints_customer FOREIGN KEY (customer_id) REFERENCES users(id),
  ADD CONSTRAINT fk_complaints_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
  ADD CONSTRAINT fk_complaints_reported_user FOREIGN KEY (reported_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  ADD CONSTRAINT fk_complaints_admin FOREIGN KEY (handled_by_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  ADD CONSTRAINT ck_complaints_reporter CHECK (customer_id IS NOT NULL OR reporter_id IS NOT NULL),
  ADD CONSTRAINT ck_complaints_type CHECK (complaint_type IN ('ORDER','USER','PROVIDER','SERVICE','PAYMENT','OTHER')),
  ADD INDEX idx_complaints_reporter (reporter_id, created_at),
  ADD INDEX idx_complaints_reported_user (reported_user_id, created_at);

ALTER TABLE conversations
  ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

UPDATE messages SET sent_at=CURRENT_TIMESTAMP(6) WHERE sent_at IS NULL;
ALTER TABLE messages MODIFY COLUMN sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- New catalogue and normalized skill mappings.
CREATE TABLE services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(80) NULL,
    description VARCHAR(2500) NOT NULL,
    pricing_type VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    price DECIMAL(12,2) NOT NULL,
    duration_minutes INT NULL,
    cover_image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    rating DOUBLE NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_services_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_services_pricing_type CHECK (pricing_type IN ('HOURLY','FIXED')),
    CONSTRAINT ck_services_price CHECK (price >= 0),
    CONSTRAINT ck_services_duration CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT ck_services_status CHECK (status IN ('DRAFT','PUBLISHED','PAUSED','ARCHIVED')),
    CONSTRAINT ck_services_rating CHECK (rating >= 0 AND rating <= 5),
    CONSTRAINT ck_services_review_count CHECK (review_count >= 0),
    INDEX idx_services_provider_status (provider_id, status),
    INDEX idx_services_status_price (status, price),
    INDEX idx_services_category_status (category, status),
    INDEX idx_services_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE bookings ADD CONSTRAINT fk_bookings_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT;
CREATE TABLE service_skills (
    service_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (service_id, skill_id),
    CONSTRAINT fk_service_skills_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_service_skills_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_service_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE service_request_skills (
    request_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (request_id, skill_id),
    CONSTRAINT fk_request_skills_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_request_skills_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_request_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE oauth_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    identity_provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email_at_provider VARCHAR(160) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_provider_subject UNIQUE (identity_provider, provider_subject),
    CONSTRAINT uq_oauth_user_provider UNIQUE (user_id, identity_provider),
    CONSTRAINT fk_oauth_identity_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_oauth_email (email_at_provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    checked_out_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_carts_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE','CHECKED_OUT','ABANDONED')),
    INDEX idx_carts_customer_status (customer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price_snapshot DECIMAL(12,2) NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_cart_item_service UNIQUE (cart_id, service_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_cart_items_price CHECK (unit_price_snapshot >= 0),
    INDEX idx_cart_items_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rental_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    service_id BIGINT NULL,
    provider_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    service_name_snapshot VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_order_item_booking UNIQUE (booking_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_amounts CHECK (unit_price >= 0 AND line_total >= 0),
    INDEX idx_order_items_order (order_id),
    INDEX idx_order_items_service (service_id),
    INDEX idx_order_items_provider (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rental_order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_order_history_order_date (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    gateway_name VARCHAR(40) NOT NULL,
    merchant_reference VARCHAR(120) NOT NULL,
    gateway_transaction_id VARCHAR(180) NULL,
    idempotency_key VARCHAR(120) NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    response_code VARCHAR(80) NULL,
    failure_reason VARCHAR(1000) NULL,
    initiated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_attempt_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT uq_payment_attempt_merchant_ref UNIQUE (merchant_reference),
    CONSTRAINT uq_payment_attempt_gateway_txn UNIQUE (gateway_name, gateway_transaction_id),
    CONSTRAINT uq_payment_attempt_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_attempt_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_payment_attempt_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_attempt_status CHECK (status IN ('CREATED','PENDING','SUCCEEDED','FAILED','CANCELLED')),
    INDEX idx_payment_attempt_payment_status (payment_id, status),
    INDEX idx_payment_attempt_created (initiated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    processed_by_admin_id BIGINT NULL,
    amount DECIMAL(12,2) NOT NULL,
    reason VARCHAR(1200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    gateway_refund_id VARCHAR(180) NULL,
    admin_note VARCHAR(1200) NULL,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_requester FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_admin FOREIGN KEY (processed_by_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_refunds_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','PROCESSING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT uq_refunds_gateway_refund UNIQUE (gateway_refund_id),
    INDEX idx_refunds_payment_status (payment_id, status),
    INDEX idx_refunds_status_requested (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add the review -> catalogue relation after the services table has been created.
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT;
