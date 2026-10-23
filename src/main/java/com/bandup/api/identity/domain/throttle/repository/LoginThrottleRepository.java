package com.bandup.api.identity.domain.throttle.repository;

import java.time.Instant;
import java.util.Optional;

import com.bandup.api.identity.domain.throttle.aggregate.LoginThrottle;
import com.bandup.api.identity.domain.throttle.valueobject.ThrottleKey;

/** Stores throttles. Implemented in infrastructure. */
public interface LoginThrottleRepository {

    Optional<LoginThrottle> find(ThrottleKey key);

    /** The throttle for {@code key} (a fresh one if none), locked until the transaction ends. */
    LoginThrottle lock(ThrottleKey key, Instant now);

    void save(LoginThrottle throttle);

    void delete(ThrottleKey key);

    /** Removes throttles that are neither counting nor locked since {@code cutoff}; returns how many. */
    int deleteIdleSince(Instant cutoff);
}
