-- Registration OTP, compatible with existing users and historical verification links.
-- Back up your MySQL database first. Apply AFTER migration v5-to-v6.
-- The existing email_verification_token column now holds a BCrypt hash of the 6-digit OTP.
-- Pending accounts with an old UUID token can still use their existing verification link
-- (until its original expiration); otherwise request a new OTP via /verify-otp.
ALTER TABLE users
    ADD COLUMN email_verification_attempts INT NOT NULL DEFAULT 0
        AFTER email_verification_expires_at,
    ADD COLUMN email_verification_last_sent_at DATETIME(6) NULL
        AFTER email_verification_attempts;
