package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.domain.throttle.aggregate.LoginThrottle;
import com.bandup.api.identity.domain.throttle.repository.LoginThrottleRepository;
import com.bandup.api.identity.domain.throttle.valueobject.ThrottleKey;
import com.bandup.api.identity.domain.throttle.valueobject.ThrottlePolicy;
import com.bandup.api.shared.application.TooManyRequestsException;

/**
 * Limits failed sign-ins (backend requirement 8.12) per email and per client
 * address. Each method is its own short transaction, so the slow password
 * check in {@link Login} never runs inside one.
 */
@Service
@RequiredArgsConstructor
public class LoginThrottling {

    private final LoginThrottleRepository throttles;
    private final Clock clock;

    /** @throws TooManyRequestsException if the email or the client is locked */
    @Transactional(readOnly = true)
    public void checkAllowed(String email, String clientAddress) {
        Instant now = clock.instant();
        Duration wait = keys(email, clientAddress).stream()
                .map(key -> throttles.find(key).map(t -> t.retryAfter(now)).orElse(Duration.ZERO))
                .max(Duration::compareTo).orElse(Duration.ZERO);
        if (!wait.isZero()) {
            throw new TooManyRequestsException(message(wait), wait);
        }
    }

    /** One wrong password for this email and client (rows locked, so concurrent failures all count). */
    @Transactional
    public void recordFailure(String email, String clientAddress) {
        Instant now = clock.instant();
        for (ThrottleKey key : keys(email, clientAddress)) {
            LoginThrottle throttle = throttles.lock(key, now);
            throttle.recordFailure(now, key.isEmail() ? ThrottlePolicy.PER_EMAIL : ThrottlePolicy.PER_CLIENT);
            throttles.save(throttle);
        }
    }

    /** A successful sign-in clears the email's count (not the client's: others may share the address). */
    @Transactional
    public void recordSuccess(String email) {
        throttles.delete(ThrottleKey.email(email));
    }

    private static List<ThrottleKey> keys(String email, String clientAddress) {
        return clientAddress == null || clientAddress.isBlank()
                ? List.of(ThrottleKey.email(email))
                : List.of(ThrottleKey.email(email), ThrottleKey.client(clientAddress));
    }

    private static String message(Duration wait) {
        long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
        return "Too many sign-in attempts. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }
}
