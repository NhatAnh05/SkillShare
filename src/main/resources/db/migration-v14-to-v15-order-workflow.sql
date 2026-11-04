-- MySQL 8: apply once to a v14 database after a backup, before running ddl-auto=validate.
ALTER TABLE bookings
 ADD COLUMN schedule_mode VARCHAR(24) NOT NULL DEFAULT 'APPOINTMENT',
 ADD COLUMN delivery_due_at DATETIME(6) NULL;
UPDATE bookings SET delivery_due_at=TIMESTAMPADD(HOUR, COALESCE(duration_hours,2), TIMESTAMP(booking_date,time_slot));
ALTER TABLE rental_orders
 ADD COLUMN work_started_at DATETIME(6) NULL,
 ADD COLUMN delivered_at DATETIME(6) NULL,
 ADD COLUMN estimated_completion_at DATETIME(6) NULL,
 ADD COLUMN progress_percent INT NOT NULL DEFAULT 0;
UPDATE rental_orders o JOIN bookings b ON b.id=o.booking_id SET o.estimated_completion_at=b.delivery_due_at;
CREATE TABLE order_work_updates (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 provider_id BIGINT NOT NULL,
 description VARCHAR(2000) NOT NULL,
 progress_percent INT NOT NULL,
 estimated_completion_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_work_update_order (order_id,id),
 CONSTRAINT fk_work_update_order FOREIGN KEY (order_id) REFERENCES rental_orders(id),
 CONSTRAINT fk_work_update_provider FOREIGN KEY (provider_id) REFERENCES users(id),
 CONSTRAINT chk_work_update_percent CHECK (progress_percent BETWEEN 0 AND 99)
);
-- Legacy timestamps and progress are intentionally left unknown instead of inventing work history.
