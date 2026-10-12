-- MySQL existing installation: back up database first.
ALTER TABLE users ADD COLUMN two_factor_preference BOOLEAN NOT NULL DEFAULT FALSE;
-- UI preference only, no real OTP/2FA authentication.
