-- SkillShare schema v4 -> v5: VNPay Sandbox wallet top-up records.
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
