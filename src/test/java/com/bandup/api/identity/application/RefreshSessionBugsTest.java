package com.bandup.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.shared.application.UnauthenticatedException;

/**
 * Regression tests for two refresh bugs: concurrent refreshes of one token
 * (bug 1) and a disabled user's refresh (bug 2). Real transactions, no
 * test-wide transaction.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshSessionBugsTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired RegisterLearner register;
    @Autowired Login login;
    @Autowired RefreshSession refreshSession;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanAccounts() {
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from app_user");
        register.register(new RegisterLearner.Command("aysel@example.com", PASSWORD, "Aysel", "az", "az"));
    }

    /** Bug 1: without a lock, several refreshes of the same token all succeed and fork the session. */
    @Test
    void concurrentRefreshesOfOneTokenLetAtMostOneThrough() throws Exception {
        String cookie = login.login(new Login.Command("aysel@example.com", PASSWORD)).refreshToken();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    refreshSession.refresh(cookie);
                    return true;
                } catch (UnauthenticatedException rejected) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            succeeded += result.get() ? 1 : 0;
        }
        pool.shutdown();

        // The 1st rotates; the 2nd counts as a lost reply (grace window) and rotates once more; any further
        // copy is theft and ends the session. The chain never forks: each token has at most one successor.
        assertThat(succeeded).isLessThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class)).isLessThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("select count(distinct replaced_by) = count(replaced_by) from refresh_token", Boolean.class))
                .isTrue();
        if (succeeded == 2) {
            assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class))
                    .as("8 copies at once: the session ends").isZero();
        }
    }

    /**
     * A thief replays the old token while the real user refreshes with the new
     * one, at the same moment. Locking only the presented token can't stop this
     * (they're different rows); locking the whole Session aggregate does.
     * Whichever runs first, the session must end fully revoked.
     */
    @Test
    void reuseRacingWithALegitimateRefreshLeavesNoUsableToken() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int round = 0; round < 10; round++) {
            String old = login.login(new Login.Command("aysel@example.com", PASSWORD)).refreshToken();
            String current = refreshSession.refresh(old).refreshToken();
            // Outside the grace window: within 30 s a replayed old token can't be told from a lost reply.
            jdbc.update("update refresh_token set issued_at = issued_at - interval '60 seconds' where replaced_by is null");
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> both = new ArrayList<>();
            for (String token : List.of(old, current)) {
                both.add(pool.submit(() -> {
                    start.await();
                    try {
                        refreshSession.refresh(token);
                    } catch (UnauthenticatedException rejected) {
                        // expected for at least one of them
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : both) {
                f.get();
            }
            assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class))
                    .as("round %d: no token of the attacked session may survive", round).isZero();
        }
        pool.shutdown();
    }

    /** Bug 2: a disabled user's refresh must end the WHOLE session and create nothing new. */
    @Test
    void aDisabledUsersRefreshRevokesTheWholeFamilyWithoutRotating() {
        String first = login.login(new Login.Command("aysel@example.com", PASSWORD)).refreshToken();
        String second = refreshSession.refresh(first).refreshToken(); // family: first (replaced), second
        jdbc.update("update app_user set status = 'DISABLED'");

        try {
            refreshSession.refresh(second);
        } catch (UnauthenticatedException expected) {
            // fine
        }

        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class))
                .as("no new token is created for a disabled user").isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class))
                .as("every token of the session is revoked").isZero();
    }
}
