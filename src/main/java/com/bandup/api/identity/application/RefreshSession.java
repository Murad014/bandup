package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.application.port.AuthSettings;
import com.bandup.api.identity.application.port.TokenGenerator;
import com.bandup.api.identity.application.port.TokenGenerator.GeneratedToken;
import com.bandup.api.identity.domain.session.aggregate.Session;
import com.bandup.api.identity.domain.session.repository.SessionRepository;
import com.bandup.api.identity.domain.session.valueobject.TokenHash;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.shared.application.UnauthenticatedException;

/**
 * Use case: trade a refresh token for a new access token and a new refresh
 * token (requirement 8.4). The {@link Session} aggregate decides: rotate,
 * or (reuse) revoke the whole session. A user disabled or deleted since
 * signing in also ends the whole session.
 *
 * <ul>
 *   <li>The session is locked for the whole transaction, so concurrent uses of
 *       any of its tokens run one after another.</li>
 *   <li>Revocations must be saved even though the request then fails with 401,
 *       so {@link UnauthenticatedException} doesn't roll the transaction back.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshSession {

    static final String SESSION_ENDED = "Your session has ended. Please sign in again.";

    private final UserRepository users;
    private final SessionRepository sessions;
    private final TokenGenerator tokenGenerator;
    private final AuthSettings settings;
    private final SessionIssuer issuer;
    private final Clock clock;

    @Transactional(noRollbackFor = UnauthenticatedException.class)
    public SignedIn refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthenticatedException(SESSION_ENDED);
        }
        TokenHash presented = tokenGenerator.hash(rawRefreshToken);
        Session session = sessions.findByTokenHash(presented)
                .orElseThrow(() -> new UnauthenticatedException(SESSION_ENDED));
        Instant now = clock.instant();

        // Check the user first: a disabled or deleted user's whole session ends, and nothing new is issued.
        Optional<User> user = users.findById(session.userId()).filter(User::canAuthenticate);
        if (user.isEmpty()) {
            session.revoke(now);
            sessions.save(session);
            throw new UnauthenticatedException(SESSION_ENDED);
        }

        GeneratedToken next = tokenGenerator.generate();
        Session.Outcome outcome = session.present(presented, next.hash(), now, settings.refreshTokenLifetime());
        sessions.save(session);
        if (!(outcome instanceof Session.Rotated rotated)) {
            throw new UnauthenticatedException(SESSION_ENDED);
        }
        return issuer.signedIn(user.get(), next.raw(), rotated.next(), now);
    }
}
