package com.bandup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The production profile starts with the values deploy/.env provides (the
 * database comes from Testcontainers here) and doesn't allow the local
 * shortcuts: no short admin password, a required JWT secret.
 */
@SpringBootTest(properties = {
        "BANDUP_DB_URL=jdbc:postgresql://replaced-by-testcontainers/bandup",
        "BANDUP_DB_USERNAME=unused", "BANDUP_DB_PASSWORD=unused",
        "BANDUP_JWT_SECRET=a-production-like-secret-that-is-long-enough-123",
        "BANDUP_ADMIN_EMAIL=owner@bandup.test", "BANDUP_ADMIN_PASSWORD=a-long-admin-password-1",
        "BANDUP_PUBLIC_URL=https://bandup.test", "BANDUP_MAIL_FROM=BandUp <no-reply@bandup.test>",
        "BANDUP_SMTP_HOST=smtp.bandup.test", "BANDUP_SMTP_USERNAME=unused", "BANDUP_SMTP_PASSWORD=unused",
        "bandup.media.dir=build/test-media"})
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProductionProfileTest {

    @Autowired Environment environment;
    @Value("${server.forward-headers-strategy}") String forwardHeaders;
    @Value("${bandup.jwt.require-secret}") boolean requireSecret;

    @Test
    void theProductionProfileStartsWithItsSafeSettings() {
        assertThat(environment.getActiveProfiles()).containsExactly("prod");
        assertThat(forwardHeaders).isEqualTo("native");
        assertThat(requireSecret).isTrue();
        assertThat(environment.getProperty("bandup.auth.allow-short-admin-password", Boolean.class, false)).isFalse();
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("bandup.auth.secure-cookie", Boolean.class, true)).isTrue(); // HTTPS only
        assertThat(environment.getProperty("bandup.mail.enabled", Boolean.class)).isTrue();          // real emails
        assertThat(environment.getProperty("bandup.auth.public-url")).isEqualTo("https://bandup.test");
    }
}
