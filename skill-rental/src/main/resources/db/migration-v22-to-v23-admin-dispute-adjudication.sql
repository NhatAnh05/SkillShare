-- SkillShare Phiên 9. Execute ONCE on MySQL 8+ AFTER migration v21 -> v22.
-- Back up the live database. This does not auto-resolve any historical disputes.
ALTER TABLE escrow_settlements
    DROP CHECK ck_escrow_settlement_kind,
    ADD CONSTRAINT ck_escrow_settlement_kind
       CHECK (kind IN ('COMPLETION', 'CUSTOMER_CANCELLATION', 'PROVIDER_CANCELLATION', 'ADMIN_DISPUTE'));

CREATE TABLE escrow_dispute_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    provider_proposed DECIMAL(14,2) NULL,
    provider_awarded DECIMAL(14,2) NOT NULL,
    customer_refunded DECIMAL(14,2) NOT NULL,
    decision_reason VARCHAR(2000) NOT NULL,
    decided_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_escrow_dispute_order UNIQUE (order_id),
    CONSTRAINT fk_escrow_dispute_order FOREIGN KEY (order_id) REFERENCES rental_orders(id),
    CONSTRAINT fk_escrow_dispute_admin FOREIGN KEY (admin_id) REFERENCES users(id),
    CONSTRAINT ck_escrow_dispute_amounts CHECK (provider_awarded >= 0 AND customer_refunded >= 0),
    INDEX idx_escrow_dispute_decided_at (decided_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
