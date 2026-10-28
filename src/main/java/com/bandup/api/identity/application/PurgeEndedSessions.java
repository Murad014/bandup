package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.domain.session.repository.SessionRepository;
import com.bandup.api.identity.domain.throttle.repository.LoginThrottleRepository;

/**
 * Daily housekeeping: deletes sessions that ended (every token expired or
 * revoked) more than a day ago, and sign-in counters idle for a day. A new
 * refresh token is stored on every page load, so without this the table only
 * grows. Emailed links that expired or were used a day ago go too. Live sessions and active locks are never touched.
 */
@Service
@RequiredArgsConstructor
public class PurgeEndedSessions {

    /** Kept a little after they end, so a just-revoked session still answers "revoked". */
    static final Duration KEEP = Duration.ofDays(1);

    private final SessionRepository sessions;
    private final LoginThrottleRepository throttles;
    private final com.bandup.api.identity.domain.token.repository.AccountTokenRepository links;
    private final Clock clock;

    /** Returns {deleted tokens, deleted counters, deleted email links}. */
    @Transactional
    public int[] purge() {
        Instant cutoff = clock.instant().minus(KEEP);
        return new int[] {sessions.deleteEndedBefore(cutoff), throttles.deleteIdleSince(cutoff),
                links.deleteEndedBefore(cutoff)};
    }
}
