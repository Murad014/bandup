package com.bandup.api.identity.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.application.port.TokenGenerator;
import com.bandup.api.identity.domain.session.repository.SessionRepository;

/**
 * Use case: sign out (requirement 8.5). Revokes the whole session of the
 * presented refresh token. Idempotent: an unknown or missing token is fine.
 */
@Service
public class Logout {

    private final SessionRepository sessions;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public Logout(SessionRepository sessions, TokenGenerator tokenGenerator, Clock clock) {
        this.sessions = sessions;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        sessions.findByTokenHash(tokenGenerator.hash(rawRefreshToken)).ifPresent(session -> {
            session.revoke(clock.instant());
            sessions.save(session);
        });
    }
}
