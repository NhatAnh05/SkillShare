package com.skillrental.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalDemoProviderUnlockConfigTest {
    @TempDir Path directory;

    private JdbcTemplate database(String name) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:file:" + directory.resolve(name) + ";DATABASE_TO_LOWER=TRUE", "sa", ""));
    }

    private void init(JdbcTemplate jdbc, int count) {
        jdbc.execute("CREATE TABLE users (email VARCHAR(255) PRIMARY KEY, role VARCHAR(20), "
                + "account_status VARCHAR(30), email_verified BOOLEAN)");
        for (int i = 1; i <= count; i++) jdbc.update("INSERT INTO users VALUES (?, 'PROVIDER', 'LOCKED', TRUE)",
                "seed.provider%02d@skillshare.invalid".formatted(i));
        jdbc.update("INSERT INTO users VALUES ('real@example.test', 'PROVIDER', 'LOCKED', TRUE)");
        jdbc.update("INSERT INTO users VALUES ('seed.customer01@skillshare.invalid', 'CUSTOMER', 'LOCKED', TRUE)");
    }

    private void run(JdbcTemplate jdbc) throws Exception {
        new LocalDemoProviderUnlockConfig().unlockLocalDemoProviders(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()))).run();
    }

    @Test void unlocksExactly49SeedProvidersAndKeepsOtherUsersLocked() throws Exception {
        var jdbc = database("scoped"); init(jdbc, 49); run(jdbc);
        assertEquals(49, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE account_status='ACTIVE'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE account_status='LOCKED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM skillshare_demo_tasks", Integer.class));
    }

    @Test void fileDatabaseAndMarkerPreserveAdminLockOnNextStartup() throws Exception {
        var jdbc = database("restart"); init(jdbc, 49); run(jdbc);
        jdbc.update("UPDATE users SET account_status='LOCKED' WHERE email='seed.provider01@skillshare.invalid'");
        var reopened = database("restart"); run(reopened);
        assertEquals("LOCKED", reopened.queryForObject("SELECT account_status FROM users "
                + "WHERE email='seed.provider01@skillshare.invalid'", String.class));
        assertEquals(48, reopened.queryForObject("SELECT COUNT(*) FROM users WHERE account_status='ACTIVE'", Integer.class));
    }

    @Test void missingDemoDataDoesNotPartiallyUnlockOrMarkComplete() throws Exception {
        var jdbc = database("incomplete"); init(jdbc, 48); run(jdbc);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE account_status='ACTIVE'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM skillshare_demo_tasks", Integer.class));
        jdbc.update("INSERT INTO users VALUES ('seed.provider49@skillshare.invalid', 'PROVIDER', 'LOCKED', TRUE)");
        run(jdbc);
        assertEquals(49, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE account_status='ACTIVE'", Integer.class));
    }
}
