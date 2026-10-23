package com.bandup.api.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.bandup.api.identity.domain.throttle.aggregate.LoginThrottle;
import com.bandup.api.identity.domain.throttle.repository.LoginThrottleRepository;
import com.bandup.api.identity.domain.throttle.valueobject.ThrottleKey;

/**
 * {@link LoginThrottleRepository} on the {@code login_throttle} table with plain
 * SQL (one small row per key). {@link #lock} creates the row if needed and
 * takes a row lock, so concurrent failures for one key are counted one after
 * another.
 */
@Repository
@RequiredArgsConstructor
class JdbcLoginThrottleRepository implements LoginThrottleRepository {

    private final JdbcClient jdbc;

    @Override
    public Optional<LoginThrottle> find(ThrottleKey key) {
        return jdbc.sql("select failures, window_started_at, locked_until from login_throttle where throttle_key = ?")
                .param(key.value())
                .query((rs, i) -> LoginThrottle.restore(key, rs.getInt("failures"),
                        rs.getTimestamp("window_started_at").toInstant(),
                        rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant()))
                .optional();
    }

    @Override
    public LoginThrottle lock(ThrottleKey key, Instant now) {
        jdbc.sql("insert into login_throttle (throttle_key, failures, window_started_at) values (?, 0, ?)"
                        + " on conflict (throttle_key) do nothing")
                .param(key.value()).param(Timestamp.from(now)).update();
        jdbc.sql("select 1 from login_throttle where throttle_key = ? for update").param(key.value()).query(Integer.class).single();
        return find(key).orElseThrow();
    }

    @Override
    public void save(LoginThrottle throttle) {
        jdbc.sql("""
                insert into login_throttle (throttle_key, failures, window_started_at, locked_until) values (?, ?, ?, ?)
                on conflict (throttle_key) do update set failures = excluded.failures,
                    window_started_at = excluded.window_started_at, locked_until = excluded.locked_until""")
                .param(throttle.key().value()).param(throttle.failures()).param(Timestamp.from(throttle.windowStartedAt()))
                .param(throttle.lockedUntil().map(Timestamp::from).orElse(null))
                .update();
    }

    @Override
    public void delete(ThrottleKey key) {
        jdbc.sql("delete from login_throttle where throttle_key = ?").param(key.value()).update();
    }

    @Override
    public int deleteIdleSince(Instant cutoff) {
        return jdbc.sql("delete from login_throttle where window_started_at < ? and (locked_until is null or locked_until < ?)")
                .param(Timestamp.from(cutoff)).param(Timestamp.from(cutoff)).update();
    }
}
