-- MySQL 8: apply once after migration v17 -> v18, after a full backup.
-- Rate-limit OTP resend requests; no OTP plaintext is persisted.
ALTER TABLE rental_orders
    ADD COLUMN completion_otp_last_sent_at DATETIME(6) NULL,
    ADD COLUMN completion_otp_resend_count INT NOT NULL DEFAULT 0;
-- Historical OTP sends have no known timestamps: do not invent them.
