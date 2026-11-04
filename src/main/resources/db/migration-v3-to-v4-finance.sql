-- SkillShare schema v3 -> v4: platform commission and OTP-gated demo withdrawals.
-- BACK UP the database first and run this migration once on an existing MySQL schema.
ALTER TABLE rental_orders
    ADD COLUMN platform_fee_amount DECIMAL(12,2) NULL AFTER settled_at,
    ADD COLUMN provider_net_amount DECIMAL(12,2) NULL AFTER platform_fee_amount;

-- Existing settled demo rows are brought into line with the same default 5% policy.
-- New settlement transactions persist these amounts through EscrowService.
UPDATE rental_orders
SET platform_fee_amount = ROUND((CASE WHEN status='CANCELLED' THEN COALESCE(cancellation_compensation, 0) ELSE total END) * 0.05, 2),
    provider_net_amount = (CASE WHEN status='CANCELLED' THEN COALESCE(cancellation_compensation, 0) ELSE total END)
       - ROUND((CASE WHEN status='CANCELLED' THEN COALESCE(cancellation_compensation, 0) ELSE total END) * 0.05, 2)
WHERE settled_at IS NOT NULL;

CREATE TABLE provider_withdrawals (
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
