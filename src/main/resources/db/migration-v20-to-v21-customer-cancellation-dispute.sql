-- SkillShare Phiên 7. For an existing MySQL 8 database, run exactly once AFTER v19 -> v20.
-- Back up first; no historical compensation or evidence is fabricated.
ALTER TABLE rental_orders
  ADD COLUMN cancellation_customer_reason VARCHAR(1000) NULL,
  ADD COLUMN cancellation_work_summary VARCHAR(2000) NULL,
  ADD COLUMN cancellation_evidence VARCHAR(2000) NULL,
  ADD COLUMN cancellation_proposed_at DATETIME(6) NULL,
  ADD COLUMN cancellation_dispute_reason VARCHAR(1000) NULL,
  ADD COLUMN cancellation_disputed_at DATETIME(6) NULL;

-- The old status CHECK rejects CANCELLATION_DISPUTED in MySQL 8.0.16+.
ALTER TABLE rental_orders DROP CHECK ck_rental_orders_status;
ALTER TABLE rental_orders ADD CONSTRAINT ck_rental_orders_status
  CHECK (status IN ('PENDING_PAYMENT','PAID','PENDING_PROVIDER_ACCEPTANCE',
                   'PENDING_START_CONFIRMATION','PROCESSING','PENDING_CUSTOMER_ACCEPTANCE',
                   'PENDING_PAYMENT_OTP','CANCELLATION_REQUESTED_BY_CUSTOMER',
                   'CANCELLATION_COMPENSATION_PENDING_CUSTOMER','CANCELLATION_DISPUTED',
                   'COMPLETED','CANCELLED'));

-- Existing history can include lengthy statuses; prevent truncation in transitions.
ALTER TABLE rental_order_status_history MODIFY COLUMN previous_status VARCHAR(50) NULL;
