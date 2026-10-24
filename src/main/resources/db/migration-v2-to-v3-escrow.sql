-- SkillShare schema v2 -> escrow/wallet schema v3.
-- Backup first. Run once against an existing MySQL 8.0.16+ database.
ALTER TABLE rental_orders
    ADD COLUMN locked_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER cancelled_by_user_id,
    ADD COLUMN provider_accepted BOOLEAN NOT NULL DEFAULT FALSE AFTER locked_amount,
    ADD COLUMN customer_started BOOLEAN NOT NULL DEFAULT FALSE AFTER provider_accepted,
    ADD COLUMN provider_started BOOLEAN NOT NULL DEFAULT FALSE AFTER customer_started,
    ADD COLUMN start_deposit_amount DECIMAL(12,2) NULL AFTER provider_started,
    ADD COLUMN provider_completed BOOLEAN NOT NULL DEFAULT FALSE AFTER start_deposit_amount,
    ADD COLUMN customer_accepted_completion BOOLEAN NOT NULL DEFAULT FALSE AFTER provider_completed,
    ADD COLUMN completion_otp_hash VARCHAR(100) NULL AFTER customer_accepted_completion,
    ADD COLUMN completion_otp_expires_at DATETIME(6) NULL AFTER completion_otp_hash,
    ADD COLUMN completion_otp_attempts INT NOT NULL DEFAULT 0 AFTER completion_otp_expires_at,
    ADD COLUMN cancellation_compensation DECIMAL(12,2) NULL AFTER completion_otp_attempts,
    ADD COLUMN cancellation_started_at DATETIME(6) NULL AFTER cancellation_compensation,
    ADD COLUMN settled_at DATETIME(6) NULL AFTER cancellation_started_at;

ALTER TABLE rental_orders DROP CHECK ck_rental_orders_status;
ALTER TABLE rental_orders MODIFY COLUMN status VARCHAR(50) NOT NULL;
ALTER TABLE rental_orders ADD CONSTRAINT ck_rental_orders_status CHECK (status IN (
    'PENDING_PAYMENT','PAID','PENDING_PROVIDER_ACCEPTANCE','PENDING_START_CONFIRMATION',
    'PROCESSING','PENDING_CUSTOMER_ACCEPTANCE','PENDING_PAYMENT_OTP',
    'CANCELLATION_REQUESTED_BY_CUSTOMER','CANCELLATION_COMPENSATION_PENDING_CUSTOMER',
    'COMPLETED','CANCELLED'
));

CREATE TABLE user_wallets (
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

CREATE TABLE wallet_transactions (
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
