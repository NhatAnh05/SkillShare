-- ============================================================================
-- SkillShare / Skill Rental - Canonical MySQL 8.0.16+ schema
-- Designed against the Group 4 feature plan and the current Spring Boot/JPA app.
-- Intended for a NEW database. Back up any existing database before applying.
-- Keep Java entity/table/column/enum names in sync with this canonical schema.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS skill_rental
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE skill_rental;

-- 1. ACCOUNT / AUTHENTICATION -------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt hash; never store plaintext passwords',
    phone VARCHAR(20) NULL,
    role VARCHAR(20) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    bio VARCHAR(1200) NULL,
    email_verified BIT(1) NOT NULL DEFAULT b'0',
    email_verification_token VARCHAR(120) NULL,
    email_verification_expires_at DATETIME(6) NULL,
    email_verification_attempts INT NOT NULL DEFAULT 0,
    email_verification_last_sent_at DATETIME(6) NULL,
    reset_token VARCHAR(120) NULL,
    reset_token_expires_at DATETIME(6) NULL,
    account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME(6) NULL,
    two_factor_preference BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_reset_token UNIQUE (reset_token),
    CONSTRAINT uq_users_email_verification_token UNIQUE (email_verification_token),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER','PROVIDER','ADMIN')),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','LOCKED','DELETED')),
    INDEX idx_users_role_status (role, account_status),
    INDEX idx_users_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS oauth_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    identity_provider VARCHAR(30) NOT NULL COMMENT 'GOOGLE for current feature; extensible to other identity providers',
    provider_subject VARCHAR(255) NOT NULL COMMENT 'Stable subject/user ID returned by the OAuth provider',
    email_at_provider VARCHAR(160) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_provider_subject UNIQUE (identity_provider, provider_subject),
    CONSTRAINT uq_oauth_user_provider UNIQUE (user_id, identity_provider),
    CONSTRAINT fk_oauth_identity_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_oauth_email (email_at_provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    location VARCHAR(255) NULL,
    preferred_skills VARCHAR(255) NULL COMMENT 'Legacy form field; normalized skill preferences can be added later',
    PRIMARY KEY (id),
    CONSTRAINT uq_customer_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS provider_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    headline VARCHAR(255) NOT NULL,
    hourly_rate DECIMAL(12,2) NOT NULL,
    rating DOUBLE NOT NULL DEFAULT 0,
    review_count INT NOT NULL DEFAULT 0,
    premium BIT(1) NOT NULL DEFAULT b'0',
    introduction VARCHAR(1200) NULL,
    availability VARCHAR(255) NULL,
    is_approved BIT(1) NOT NULL DEFAULT b'0',
    profile_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_provider_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_provider_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_provider_hourly_rate CHECK (hourly_rate >= 0),
    CONSTRAINT ck_provider_rating CHECK (rating >= 0 AND rating <= 5),
    CONSTRAINT ck_provider_review_count CHECK (review_count >= 0),
    CONSTRAINT ck_provider_profile_status CHECK (profile_status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    INDEX idx_provider_profiles_rate (hourly_rate),
    INDEX idx_provider_profiles_rating (rating),
    INDEX idx_provider_profiles_approval (is_approved, profile_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. SKILLS AND SERVICE CATALOG ------------------------------------------------
CREATE TABLE IF NOT EXISTS skills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_skills_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS provider_skills (
    provider_profile_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (provider_profile_id, skill_id),
    CONSTRAINT fk_provider_skills_profile FOREIGN KEY (provider_profile_id) REFERENCES provider_profiles(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_provider_skills_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_provider_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_id BIGINT NOT NULL COMMENT 'users.id whose role must be PROVIDER; enforce role in service layer',
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

CREATE TABLE IF NOT EXISTS service_skills (
    service_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (service_id, skill_id),
    CONSTRAINT fk_service_skills_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_service_skills_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_service_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. CUSTOMER REQUESTS / PROVIDER PROPOSALS ----------------------------------
CREATE TABLE IF NOT EXISTS service_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    required_skills VARCHAR(255) NOT NULL COMMENT 'Retained for compatibility with current ServiceRequest entity/form',
    description VARCHAR(2500) NOT NULL,
    budget DECIMAL(12,2) NOT NULL,
    deadline DATE NOT NULL,
    status VARCHAR(50) NOT NULL,
    selected_provider_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_service_requests_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_service_requests_selected_provider FOREIGN KEY (selected_provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_service_requests_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_service_requests_budget CHECK (budget > 0),
    INDEX idx_service_requests_customer (customer_id, id),
    INDEX idx_service_requests_status_deadline (status, deadline),
    INDEX idx_service_requests_selected_provider (selected_provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS service_request_skills (
    request_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (request_id, skill_id),
    CONSTRAINT fk_request_skills_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_request_skills_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_request_skills_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS applies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    proposal VARCHAR(1800) NOT NULL,
    proposed_price DECIMAL(12,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_applies_request_provider UNIQUE (request_id, provider_id),
    CONSTRAINT fk_applies_request FOREIGN KEY (request_id) REFERENCES service_requests(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_applies_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_applies_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT ck_applies_price CHECK (proposed_price >= 0),
    INDEX idx_applies_request_date (request_id, applied_at),
    INDEX idx_applies_provider_status (provider_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. CART ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS carts (
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

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cart_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price_snapshot DECIMAL(12,2) NOT NULL COMMENT 'Price captured when added; backend decides whether to refresh at checkout',
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_cart_item_service UNIQUE (cart_id, service_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_cart_items_price CHECK (unit_price_snapshot >= 0),
    INDEX idx_cart_items_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. BOOKINGS / ORDERS --------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    service_id BIGINT NULL COMMENT 'Nullable for legacy direct-booking records; new catalog bookings should populate it',
    service_name VARCHAR(255) NOT NULL COMMENT 'Snapshot retained for compatibility and historical display',
    schedule_mode VARCHAR(24) NOT NULL DEFAULT 'APPOINTMENT',
    delivery_due_at DATETIME(6) NULL,
    booking_date DATE NOT NULL,
    time_slot TIME(6) NOT NULL,
    note VARCHAR(1200) NULL,
    idempotency_key VARCHAR(36) NULL,
    premium BIT(1) NOT NULL DEFAULT b'0',
    direct_booking BIT(1) NOT NULL DEFAULT b'1',
    duration_hours INT NOT NULL DEFAULT 2,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    cancellation_reason VARCHAR(1000) NULL,
    cancelled_by_user_id BIGINT NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT uq_bookings_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_bookings_amount CHECK (amount >= 0),
    INDEX idx_bookings_customer_date (customer_id, booking_date),
    INDEX idx_bookings_provider_date (provider_id, booking_date),
    INDEX idx_bookings_status_date (status, booking_date),
    INDEX idx_bookings_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rental_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NULL COMMENT 'Legacy shortcut for one-booking orders; keep nullable for multi-item checkout',
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NULL COMMENT 'Legacy/single-provider shortcut; NULL for multi-provider cart orders',
    subtotal DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    work_started_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    estimated_completion_at DATETIME(6) NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    cancellation_reason VARCHAR(1000) NULL,
    cancelled_by_user_id BIGINT NULL,
    locked_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    provider_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    customer_started BOOLEAN NOT NULL DEFAULT FALSE,
    provider_started BOOLEAN NOT NULL DEFAULT FALSE,
    start_deposit_amount DECIMAL(12,2) NULL,
    customer_start_confirmed_at DATETIME(6) NULL,
    provider_start_confirmed_at DATETIME(6) NULL,
    start_deposit_reserved_at DATETIME(6) NULL,
    start_deposit_closed_at DATETIME(6) NULL,
    start_deposit_state VARCHAR(24) NULL,
    provider_completed BOOLEAN NOT NULL DEFAULT FALSE,
    customer_accepted_completion BOOLEAN NOT NULL DEFAULT FALSE,
    completion_otp_hash VARCHAR(100) NULL,
    completion_otp_expires_at DATETIME(6) NULL,
    completion_otp_attempts INT NOT NULL DEFAULT 0,
    cancellation_compensation DECIMAL(12,2) NULL,
    cancellation_started_at DATETIME(6) NULL,
    settled_at DATETIME(6) NULL,
    platform_fee_amount DECIMAL(12,2) NULL,
    provider_net_amount DECIMAL(12,2) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_rental_orders_booking UNIQUE (booking_id),
    CONSTRAINT fk_rental_orders_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rental_orders_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rental_orders_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rental_orders_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_rental_orders_status CHECK (status IN ('PENDING_PAYMENT','PAID','PENDING_PROVIDER_ACCEPTANCE','PENDING_START_CONFIRMATION','PROCESSING','PENDING_CUSTOMER_ACCEPTANCE','PENDING_PAYMENT_OTP','CANCELLATION_REQUESTED_BY_CUSTOMER','CANCELLATION_COMPENSATION_PENDING_CUSTOMER','COMPLETED','CANCELLED')),
    CONSTRAINT ck_rental_orders_amounts CHECK (subtotal >= 0 AND discount >= 0 AND total >= 0 AND discount <= subtotal),
    INDEX idx_rental_orders_customer_id (customer_id, id),
    INDEX idx_rental_orders_customer_status (customer_id, status),
    INDEX idx_rental_orders_provider_status (provider_id, status),
    INDEX idx_rental_orders_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_wallets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance DECIMAL(14,2) NOT NULL DEFAULT 0,
    locked_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_wallet_user UNIQUE (user_id),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_wallet_amounts CHECK (balance >= 0 AND locked_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    balance_after DECIMAL(14,2) NOT NULL,
    locked_after DECIMAL(14,2) NOT NULL,
    description VARCHAR(800) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_wallet_tx_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_wallet_tx_wallet_date (wallet_id, created_at),
    INDEX idx_wallet_tx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS provider_withdrawals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    account_number VARCHAR(80) NOT NULL,
    account_holder VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_OTP',
    otp_hash VARCHAR(100) NULL,
    otp_expires_at DATETIME(6) NULL,
    otp_attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    verified_at DATETIME(6) NULL,
    processed_at DATETIME(6) NULL,
    processed_by_admin_id BIGINT NULL,
    admin_note VARCHAR(600) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_provider_withdrawal_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_provider_withdrawal_admin FOREIGN KEY (processed_by_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_provider_withdrawal_amount CHECK (amount > 0),
    INDEX idx_provider_withdrawal_status_created (status, created_at),
    INDEX idx_provider_withdrawal_provider (provider_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rental_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    service_id BIGINT NULL COMMENT 'Nullable for legacy/request-based work not originating from catalog',
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

CREATE TABLE IF NOT EXISTS rental_order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(50) NOT NULL,
    changed_by_user_id BIGINT NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_order_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_order_history_order_date (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. PAYMENT / REFUND ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    method VARCHAR(40) NOT NULL,
    gateway_name VARCHAR(40) NULL,
    gateway_transaction_id VARCHAR(180) NULL,
    idempotency_key VARCHAR(120) NULL,
    coupon_code VARCHAR(80) NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(1000) NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT uq_payment_gateway_transaction UNIQUE (gateway_name, gateway_transaction_id),
    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING','PAID','FAILED','PARTIALLY_REFUNDED','REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    INDEX idx_payments_status_paid_at (status, paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment_attempts (
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

CREATE TABLE IF NOT EXISTS refunds (
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

-- 7. REVIEWS / COMPLAINTS ------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    service_id BIGINT NULL,
    customer_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000) NOT NULL,
    provider_response VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_reviews_order UNIQUE (order_id),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_service FOREIGN KEY (service_id) REFERENCES services(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_reviews_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_provider_created (provider_id, created_at),
    INDEX idx_reviews_service_created (service_id, created_at),
    INDEX idx_reviews_customer_created (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaints (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NULL COMMENT 'Legacy field retained for compatibility with current ReviewService; nullable for provider-originated reports',
    reporter_id BIGINT NULL,
    reported_user_id BIGINT NULL,
    handled_by_admin_id BIGINT NULL,
    order_id BIGINT NULL,
    complaint_type VARCHAR(30) NOT NULL DEFAULT 'ORDER',
    subject VARCHAR(255) NOT NULL,
    description VARCHAR(2500) NOT NULL,
    status VARCHAR(50) NOT NULL,
    resolution_notes VARCHAR(1500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_complaints_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_complaints_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_complaints_reported_user FOREIGN KEY (reported_user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_admin FOREIGN KEY (handled_by_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_complaints_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_complaints_reporter CHECK (customer_id IS NOT NULL OR reporter_id IS NOT NULL),
    CONSTRAINT ck_complaints_type CHECK (complaint_type IN ('ORDER','USER','PROVIDER','SERVICE','PAYMENT','OTHER')),
    CONSTRAINT ck_complaints_status CHECK (status IN ('OPEN','PROCESSING','RESOLVED','REJECTED')),
    INDEX idx_complaints_status_created (status, created_at),
    INDEX idx_complaints_reporter (reporter_id, created_at),
    INDEX idx_complaints_reported_user (reported_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. CHAT ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NULL,
    provider_id BIGINT NULL,
    support_admin_id BIGINT NULL,
    support_conversation BOOLEAN NOT NULL DEFAULT FALSE,
    support_key VARCHAR(80) NULL,
    order_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_conversation_customer_provider UNIQUE (customer_id, provider_id),
    CONSTRAINT uq_conversation_support_key UNIQUE (support_key),
    CONSTRAINT fk_conversations_customer FOREIGN KEY (customer_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_conversations_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_conversations_support_admin FOREIGN KEY (support_admin_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_conversations_order FOREIGN KEY (order_id) REFERENCES rental_orders(id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_conversations_different_users CHECK (customer_id IS NULL OR provider_id IS NULL OR customer_id <> provider_id),
    INDEX idx_conversations_provider (provider_id),
    INDEX idx_conversations_activity (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2500) NOT NULL,
    sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_messages_conversation_sent (conversation_id, sent_at),
    INDEX idx_messages_sender_sent (sender_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    sender_id BIGINT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    content VARCHAR(1200) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id BIGINT NULL,
    target_url VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_notifications_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
    INDEX idx_notifications_recipient_created (recipient_id, created_at),
    INDEX idx_notifications_recipient_read (recipient_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_campaigns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    request_key CHAR(36) NOT NULL,
    title VARCHAR(180) NOT NULL,
    content VARCHAR(1200) NOT NULL,
    type VARCHAR(40) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_notification_campaign_request UNIQUE (request_key),
    CONSTRAINT fk_notification_campaign_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_notification_campaign_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_campaign_recipients (
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

CREATE TABLE IF NOT EXISTS delivery_submissions (
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

CREATE TABLE IF NOT EXISTS delivery_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id BIGINT NOT NULL,
    url VARCHAR(1000) NOT NULL,
    label VARCHAR(160) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_delivery_link_submission FOREIGN KEY (delivery_id) REFERENCES delivery_submissions(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS delivery_files (
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

CREATE TABLE IF NOT EXISTS delivery_revision_requests (
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

-- Notes:
-- * Existing one-service booking code remains supported by bookings.service_name and
--   rental_orders.booking_id/provider_id. New cart checkout uses rental_order_items.
-- * Cross-provider carts are represented by rental_orders.provider_id = NULL and each
--   rental_order_items.provider_id set to the relevant provider. Enforce order totals,
--   service ownership, role checks and the one-active-cart rule in transactional services.
-- * Use soft status changes (ARCHIVED/PAUSED/SUSPENDED), not hard deletes, for records
--   referenced by historical orders/payments/reviews.

CREATE TABLE IF NOT EXISTS wallet_topups (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    txn_ref VARCHAR(80) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    gateway_transaction_no VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    CONSTRAINT uq_wallet_topup_ref UNIQUE (txn_ref),
    CONSTRAINT fk_wallet_topup_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_wallet_topups_user_status ON wallet_topups(user_id, status);

CREATE TABLE IF NOT EXISTS support_assignment_history (
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

CREATE TABLE IF NOT EXISTS terms_acceptances (
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

-- Order work progress history
CREATE TABLE IF NOT EXISTS order_work_updates (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 provider_id BIGINT NOT NULL,
 description VARCHAR(2000) NOT NULL,
 progress_percent INT NOT NULL,
 estimated_completion_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_work_update_order (order_id,id),
 CONSTRAINT fk_work_update_order FOREIGN KEY (order_id) REFERENCES rental_orders(id),
 CONSTRAINT fk_work_update_provider FOREIGN KEY (provider_id) REFERENCES users(id),
 CONSTRAINT chk_work_update_percent CHECK (progress_percent BETWEEN 0 AND 99)
);

CREATE TABLE IF NOT EXISTS user_role_changes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 target_user_id BIGINT NOT NULL,
 actor_user_id BIGINT NOT NULL,
 previous_role VARCHAR(20) NOT NULL,
 new_role VARCHAR(20) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 changed_at DATETIME(6) NOT NULL,
 INDEX idx_role_change_time (changed_at,id),
 CONSTRAINT fk_role_change_target FOREIGN KEY (target_user_id) REFERENCES users(id),
 CONSTRAINT fk_role_change_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);
