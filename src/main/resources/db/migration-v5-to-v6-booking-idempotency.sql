-- SkillShare schema v5 -> v6: durable idempotency for booking submissions.
-- Back up the database first and run this migration once on an existing v5 MySQL schema.
ALTER TABLE bookings
    ADD COLUMN idempotency_key VARCHAR(36) NULL AFTER direct_booking,
    ADD CONSTRAINT uq_bookings_idempotency_key UNIQUE (idempotency_key);
