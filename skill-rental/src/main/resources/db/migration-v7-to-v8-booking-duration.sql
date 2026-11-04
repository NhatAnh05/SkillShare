-- Persist the duration used by centralized service pricing and booking idempotency.
-- Back up the database first. Apply only once to databases already migrated to v7.
ALTER TABLE bookings
    ADD COLUMN duration_hours INT NOT NULL DEFAULT 2 AFTER direct_booking;
