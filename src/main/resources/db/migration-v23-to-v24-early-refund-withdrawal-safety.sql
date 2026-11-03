-- SkillShare v24. Run ONCE on MySQL 8+ AFTER v22 -> v23, after taking a backup.
-- Customer early cancellation was implemented in v10 but not allowed by the MySQL CHECK constraint.
ALTER TABLE escrow_settlements
    DROP CHECK ck_escrow_settlement_kind,
    ADD CONSTRAINT ck_escrow_settlement_kind
        CHECK (kind IN ('COMPLETION', 'CUSTOMER_CANCELLATION', 'PROVIDER_CANCELLATION',
                       'ADMIN_DISPUTE', 'CUSTOMER_CANCEL_EARLY'));

-- Withdrawal OTP anti-spam metadata. Historical requests start at zero resends;
-- NULL last_sent_at allows one immediate resend after migration.
ALTER TABLE provider_withdrawals
    ADD COLUMN otp_resend_count INT NOT NULL DEFAULT 0,
    ADD COLUMN otp_last_sent_at DATETIME(6) NULL,
    ADD CONSTRAINT ck_provider_withdrawal_otp_resend_count
        CHECK (otp_resend_count >= 0 AND otp_resend_count <= 3);
