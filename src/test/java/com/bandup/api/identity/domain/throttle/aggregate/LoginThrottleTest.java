package com.bandup.api.identity.domain.throttle.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.bandup.api.identity.domain.throttle.valueobject.ThrottleKey;
import com.bandup.api.identity.domain.throttle.valueobject.ThrottlePolicy;

class LoginThrottleTest {

    private static final Instant NOW = Instant.parse("2026-10-23T09:00:00Z");
    private static final ThrottlePolicy FIVE = ThrottlePolicy.PER_EMAIL;

    @Test
    void theFifthFailureWithinTheWindowLocksForFifteenMinutes() {
        LoginThrottle throttle = LoginThrottle.fresh(ThrottleKey.email("a@b.c"), NOW);
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(NOW.plusSeconds(i), FIVE);
        }
        assertThat(throttle.isLocked(NOW.plusSeconds(4))).isFalse();

        throttle.recordFailure(NOW.plusSeconds(4), FIVE);

        assertThat(throttle.isLocked(NOW.plusSeconds(5))).isTrue();
        assertThat(throttle.retryAfter(NOW.plusSeconds(4))).isEqualTo(Duration.ofMinutes(15));
        assertThat(throttle.isLocked(NOW.plusSeconds(4).plus(Duration.ofMinutes(15)))).isFalse();
        assertThat(throttle.retryAfter(NOW.plus(Duration.ofHours(1)))).isZero();
    }

    @Test
    void failuresOutsideTheWindowStartOver() {
        LoginThrottle throttle = LoginThrottle.fresh(ThrottleKey.email("a@b.c"), NOW);
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(NOW, FIVE);
        }

        throttle.recordFailure(NOW.plus(Duration.ofMinutes(16)), FIVE); // window over: this is failure 1 again

        assertThat(throttle.isLocked(NOW.plus(Duration.ofMinutes(16)))).isFalse();
        assertThat(throttle.failures()).isEqualTo(1);
    }

    @Test
    void afterALockEndsCountingStartsFromZero() {
        LoginThrottle throttle = LoginThrottle.fresh(ThrottleKey.email("a@b.c"), NOW);
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(NOW, FIVE);
        }
        Instant afterLock = NOW.plus(Duration.ofMinutes(31));

        throttle.recordFailure(afterLock, FIVE);

        assertThat(throttle.isLocked(afterLock)).isFalse();
        assertThat(throttle.failures()).isEqualTo(1);
    }

    @Test
    void keysAreNormalisedAndTellEmailsFromClients() {
        assertThat(ThrottleKey.email("  Aysel@Example.COM ")).isEqualTo(ThrottleKey.email("aysel@example.com"));
        assertThat(ThrottleKey.email("x").isEmail()).isTrue();
        assertThat(ThrottleKey.client("10.0.0.7").isEmail()).isFalse();
        assertThat(ThrottleKey.client("10.0.0.7").value()).isEqualTo("ip:10.0.0.7");
    }
}
