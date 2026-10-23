package com.bandup.api.identity.domain.throttle.valueobject;

import java.time.Duration;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * How many failures within {@code window} lock a key, and for how long.
 *
 * <p>{@link #PER_EMAIL}: 5 in 15 minutes → 15 minutes (guessing one account's
 * password). {@link #PER_CLIENT}: 30 in 15 minutes → 15 minutes (one machine
 * trying many accounts).
 */
@ValueObject
public record ThrottlePolicy(int maxFailures, Duration window, Duration lockFor) {

    public static final ThrottlePolicy PER_EMAIL = new ThrottlePolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15));
    public static final ThrottlePolicy PER_CLIENT = new ThrottlePolicy(30, Duration.ofMinutes(15), Duration.ofMinutes(15));

    public ThrottlePolicy {
        if (maxFailures < 1 || window.isNegative() || window.isZero() || lockFor.isNegative() || lockFor.isZero()) {
            throw new IllegalArgumentException("Invalid throttle policy");
        }
    }
}
