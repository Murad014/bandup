package com.bandup.api.identity.domain.throttle.aggregate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.identity.domain.throttle.valueobject.ThrottleKey;
import com.bandup.api.identity.domain.throttle.valueobject.ThrottlePolicy;
import com.bandup.api.shared.domain.ddd.AggregateRoot;

/**
 * Failed sign-in attempts for one key (backend requirement 8.12). Failures
 * count within a window that starts with the first one; reaching the policy's
 * limit locks the key for a while. While locked, sign-in is refused before the
 * password is even checked.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class LoginThrottle {

    @EqualsAndHashCode.Include
    @Getter
    private final ThrottleKey key;
    @Getter
    private int failures;
    @Getter
    private Instant windowStartedAt;
    private Instant lockedUntil;

    private LoginThrottle(ThrottleKey key, int failures, Instant windowStartedAt, Instant lockedUntil) {
        this.key = Objects.requireNonNull(key, "key");
        if (failures < 0) {
            throw new IllegalArgumentException("Negative failures");
        }
        this.failures = failures;
        this.windowStartedAt = Objects.requireNonNull(windowStartedAt, "windowStartedAt");
        this.lockedUntil = lockedUntil;
    }

    /** No failures yet. */
    public static LoginThrottle fresh(ThrottleKey key, Instant now) {
        return new LoginThrottle(key, 0, now, null);
    }

    public static LoginThrottle restore(ThrottleKey key, int failures, Instant windowStartedAt, Instant lockedUntil) {
        return new LoginThrottle(key, failures, windowStartedAt, lockedUntil);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** How long until the lock ends (zero when not locked). */
    public Duration retryAfter(Instant now) {
        return isLocked(now) ? Duration.between(now, lockedUntil) : Duration.ZERO;
    }

    /** One more wrong password. A window that has passed starts over. */
    public void recordFailure(Instant now, ThrottlePolicy policy) {
        if (!now.isBefore(windowStartedAt.plus(policy.window()))) {
            failures = 0;
            windowStartedAt = now;
        }
        failures++;
        if (failures >= policy.maxFailures()) {
            lockedUntil = now.plus(policy.lockFor());
            failures = 0;
            windowStartedAt = now;
        }
    }

    public Optional<Instant> lockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }
}
