-- SkillShare Phiên 6: run ONCE on MySQL 8 AFTER migration v18 -> v19.
-- Back up the database. Historical completed orders are NOT backfilled with guessed fee/ledger data.
CREATE TABLE IF NOT EXISTS escrow_settlements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    settled_by_user_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    held_before DECIMAL(14,2) NOT NULL,
    gross_amount DECIMAL(14,2) NOT NULL,
    customer_refund_amount DECIMAL(14,2) NOT NULL,
    platform_fee_amount DECIMAL(14,2) NOT NULL,
    provider_net_amount DECIMAL(14,2) NOT NULL,
    commission_rate DECIMAL(9,8) NOT NULL,
    settled_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_escrow_settlement_order UNIQUE (order_id),
    CONSTRAINT fk_escrow_settlement_order FOREIGN KEY (order_id) REFERENCES rental_orders(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_escrow_settlement_actor FOREIGN KEY (settled_by_user_id) REFERENCES users(id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_escrow_settlement_kind CHECK (kind IN ('COMPLETION','CUSTOMER_CANCELLATION')),
    CONSTRAINT ck_escrow_settlement_nonnegative CHECK (held_before >= 0 AND gross_amount >= 0
        AND customer_refund_amount >= 0 AND platform_fee_amount >= 0 AND provider_net_amount >= 0),
    CONSTRAINT ck_escrow_settlement_balance CHECK (held_before = gross_amount + customer_refund_amount
        AND gross_amount = platform_fee_amount + provider_net_amount),
    CONSTRAINT ck_escrow_settlement_rate CHECK (commission_rate >= 0 AND commission_rate <= 1),
    INDEX idx_escrow_settlement_time (settled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
