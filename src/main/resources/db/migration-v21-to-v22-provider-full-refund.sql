-- SkillShare Phiên 8. RUN ONCE after migration v20 -> v21 (MySQL 8+).
-- Back up the database before running. No historic refund is inferred or backfilled.
-- Existing records stay unchanged; authorize the new one-row-per-order full-refund entry.
ALTER TABLE escrow_settlements
    DROP CHECK ck_escrow_settlement_kind,
    ADD CONSTRAINT ck_escrow_settlement_kind
        CHECK (kind IN ('COMPLETION', 'CUSTOMER_CANCELLATION', 'PROVIDER_CANCELLATION'));
