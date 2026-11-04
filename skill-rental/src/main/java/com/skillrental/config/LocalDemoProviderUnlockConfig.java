package com.skillrental.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.IntStream;

/** One-time local data migration, after the initial seed; never resets later Admin decisions. */
@Configuration
@Profile("local & !prod & !mysql")
public class LocalDemoProviderUnlockConfig {
    private static final String TASK = "unlock-49-seed-providers-v1";
    private static final List<String> EMAILS = IntStream.rangeClosed(1, 49)
            .mapToObj(i -> "seed.provider%02d@skillshare.invalid".formatted(i)).toList();

    @Bean
    @Order(1)
    CommandLineRunner unlockLocalDemoProviders(JdbcTemplate jdbc, TransactionTemplate transaction) {
        return args -> {
            // Separate persistent marker: ddl-auto=update keeps it across local restarts.
            jdbc.execute("CREATE TABLE IF NOT EXISTS skillshare_demo_tasks ("
                    + "task_key VARCHAR(100) PRIMARY KEY, completed_at TIMESTAMP NOT NULL)");
            transaction.executeWithoutResult(status -> {
                Integer done = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM skillshare_demo_tasks WHERE task_key = ?", Integer.class, TASK);
                if (done != null && done > 0) return;

                // Exact synthetic email allowlist; no broad update of real or Customer accounts.
                for (String email : EMAILS) {
                    Integer exists = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM users WHERE email = ? AND role = 'PROVIDER'", Integer.class, email);
                    if (exists == null || exists != 1) {
                        System.out.println("Local Provider unlock deferred: the 49 demo Providers are not all present.");
                        return; // Existing non-demo databases remain untouched; do not mark as completed.
                    }
                }
                int changed = 0;
                for (String email : EMAILS) {
                    changed += jdbc.update("UPDATE users SET account_status = 'ACTIVE' "
                                    + "WHERE email = ? AND role = 'PROVIDER' AND email_verified = TRUE "
                                    + "AND account_status = 'LOCKED'", email);
                }
                jdbc.update("INSERT INTO skillshare_demo_tasks (task_key, completed_at) VALUES (?, CURRENT_TIMESTAMP)", TASK);
                System.out.println("Local demo Provider unlock completed: " + changed
                        + " accounts opened. Subsequent Admin locks will be preserved.");
            });
        };
    }
}
