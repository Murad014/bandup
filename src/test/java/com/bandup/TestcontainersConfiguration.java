package com.bandup;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gives tests a throwaway PostgreSQL 16 (same engine as dev/prod), so tests
 * never touch the developer's local database. {@link ServiceConnection} points
 * the datasource at the container; Flyway then migrates it on startup.
 *
 * <p>Import it in any test that starts the application context:
 * {@code @Import(TestcontainersConfiguration.class)}. One container starts per
 * distinct cached test context (~1–2 s each), not per test method.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Every test application "sends" its account emails here instead of the log. */
    @Bean
    @org.springframework.context.annotation.Primary
    RecordingMailer recordingMailer() {
        return new RecordingMailer();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16");
    }
}
