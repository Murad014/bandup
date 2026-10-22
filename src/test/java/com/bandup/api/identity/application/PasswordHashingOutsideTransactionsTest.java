package com.bandup.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.identity.application.port.PasswordHasher;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;

/**
 * Bug 3: BCrypt (~250 ms) must never run while a database transaction (and so
 * a pooled connection) is held. A recording hasher notes whether a transaction
 * was active each time it was called.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PasswordHashingOutsideTransactionsTest.RecordingHasherConfig.class})
class PasswordHashingOutsideTransactionsTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired RegisterLearner register;
    @Autowired Login login;
    @Autowired BootstrapAdmin bootstrapAdmin;
    @Autowired RecordingHasher hasher;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from app_user");
        hasher.calls.clear();
    }

    @Test
    void registerHashesOutsideATransaction() {
        register.register(new RegisterLearner.Command("aysel@example.com", PASSWORD, null, "az", "az"));
        assertThat(hasher.calls).containsOnly("hash:no-tx");
    }

    @Test
    void loginVerifiesOutsideATransaction() {
        register.register(new RegisterLearner.Command("aysel@example.com", PASSWORD, null, "az", "az"));
        hasher.calls.clear();

        login.login(new Login.Command("aysel@example.com", PASSWORD));
        try {
            login.login(new Login.Command("nobody@example.com", PASSWORD)); // unknown email: dummy check
        } catch (com.bandup.api.shared.application.UnauthenticatedException expected) {
            // rejected, as it should be
        }
        assertThat(hasher.calls).containsOnly("matches:no-tx");
    }

    @Test
    void bootstrapAdminHashesOutsideATransaction() {
        bootstrapAdmin.ensureAdmin("murad@example.com", "a-strong-admin-password");
        assertThat(hasher.calls).containsOnly("hash:no-tx");
    }

    @TestConfiguration
    static class RecordingHasherConfig {
        @Bean
        @Primary
        RecordingHasher recordingHasher() {
            return new RecordingHasher();
        }
    }

    static class RecordingHasher implements PasswordHasher {

        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4); // fast for tests

        @Override
        public PasswordHash hash(NewPassword password) {
            calls.add("hash:" + tx());
            return new PasswordHash(bcrypt.encode(password.reveal()));
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash hash) {
            calls.add("matches:" + tx());
            return rawPassword != null && bcrypt.matches(rawPassword, hash.value());
        }

        private static String tx() {
            return TransactionSynchronizationManager.isActualTransactionActive() ? "IN-TX" : "no-tx";
        }
    }
}
