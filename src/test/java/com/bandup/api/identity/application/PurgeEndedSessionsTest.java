package com.bandup.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;

/** The nightly clean-up deletes only ended sessions and idle sign-in counters. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PurgeEndedSessionsTest {

    @Autowired PurgeEndedSessions purge;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyEndedSessionsAndIdleCountersGo() {
        long user = jdbc.queryForObject("insert into app_user (email, ui_language, support_language) values (?, 'en', 'en') returning id",
                Long.class, "purge-" + UUID.randomUUID() + "@bandup.test");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from login_throttle");

        UUID live = session(user, "now() - interval '3 days'", "now() + interval '11 days'", null);            // in use
        UUID expired = session(user, "now() - interval '20 days'", "now() - interval '6 days'", null);           // long over
        UUID loggedOut = session(user, "now() - interval '5 days'", "now() + interval '9 days'", "now() - interval '2 days'");
        UUID justLoggedOut = session(user, "now() - interval '5 days'", "now() + interval '9 days'", "now() - interval '1 hour'");
        jdbc.update("""
                insert into login_throttle (throttle_key, failures, window_started_at, locked_until) values
                  ('email:old@x.y', 2, now() - interval '3 days', null),
                  ('email:locked@x.y', 0, now() - interval '10 minutes', now() + interval '5 minutes'),
                  ('ip:10.0.0.1', 3, now() - interval '1 hour', null)""");

        int[] deleted = purge.purge();

        assertThat(families()).containsExactlyInAnyOrder(live, justLoggedOut);
        assertThat(deleted[0]).isEqualTo(4); // 2 tokens each in "expired" and "loggedOut"
        assertThat(jdbc.queryForList("select throttle_key from login_throttle", String.class))
                .containsExactlyInAnyOrder("email:locked@x.y", "ip:10.0.0.1");
        assertThat(deleted[1]).isEqualTo(1);
        assertThat(expired).isNotNull();
        assertThat(loggedOut).isNotNull();
    }

    /** A session of two tokens: the first replaced by the second (the newest). */
    private UUID session(long user, String issued, String expires, String revoked) {
        UUID family = UUID.randomUUID();
        long newest = jdbc.queryForObject("insert into refresh_token (user_id, token_hash, family_id, issued_at, expires_at, revoked_at)"
                + " values (?, ?, ?, " + issued + ", " + expires + ", " + (revoked == null ? "null" : revoked) + ") returning id",
                Long.class, user, hex(), family);
        jdbc.update("insert into refresh_token (user_id, token_hash, family_id, issued_at, expires_at, replaced_by, revoked_at)"
                + " values (?, ?, ?, " + issued + " - interval '1 day', " + expires + ", ?, " + (revoked == null ? "null" : revoked) + ")",
                user, hex(), family, newest);
        return family;
    }

    private List<UUID> families() {
        return jdbc.queryForList("select distinct family_id from refresh_token", UUID.class);
    }

    private static String hex() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
