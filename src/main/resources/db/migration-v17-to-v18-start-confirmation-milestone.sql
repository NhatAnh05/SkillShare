-- MySQL 8; apply ONCE after v16->v17, after backing up database.
-- One third of the total is earmarked INSIDE the full escrow; never debited or paid twice.
ALTER TABLE rental_orders
 ADD COLUMN customer_start_confirmed_at DATETIME(6) NULL,
 ADD COLUMN provider_start_confirmed_at DATETIME(6) NULL,
 ADD COLUMN start_deposit_reserved_at DATETIME(6) NULL,
 ADD COLUMN start_deposit_closed_at DATETIME(6) NULL,
 ADD COLUMN start_deposit_state VARCHAR(24) NULL;

-- Existing in-flight rows already having the 1/3 amount stay reserved.
-- Historical confirmation timestamps remain unknown: do not invent audit timestamps.
UPDATE rental_orders
 SET start_deposit_state = 'RESERVED'
 WHERE start_deposit_amount IS NOT NULL AND locked_amount > 0 AND status IN
 ('PROCESSING', 'PENDING_CUSTOMER_ACCEPTANCE', 'PENDING_PAYMENT_OTP',
  'CANCELLATION_REQUESTED_BY_CUSTOMER', 'CANCELLATION_COMPENSATION_PENDING_CUSTOMER');
UPDATE rental_orders
 SET start_deposit_state = 'CLOSED'
 WHERE start_deposit_amount IS NOT NULL AND status IN ('COMPLETED', 'CANCELLED');
