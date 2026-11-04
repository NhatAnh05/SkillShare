-- MySQL 8: apply once after v15, before starting ddl-auto=validate.
CREATE TABLE user_role_changes (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 target_user_id BIGINT NOT NULL,
 actor_user_id BIGINT NOT NULL,
 previous_role VARCHAR(20) NOT NULL,
 new_role VARCHAR(20) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 changed_at DATETIME(6) NOT NULL,
 INDEX idx_role_change_time (changed_at,id),
 CONSTRAINT fk_role_change_target FOREIGN KEY (target_user_id) REFERENCES users(id),
 CONSTRAINT fk_role_change_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
);
