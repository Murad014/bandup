package com.bandup.api.identity.application;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.application.port.AccessTokenIssuer;
import com.bandup.api.identity.application.port.AuthSettings;
import com.bandup.api.identity.application.port.TokenGenerator;
import com.bandup.api.identity.application.port.TokenGenerator.GeneratedToken;
import com.bandup.api.identity.domain.session.aggregate.Session;
import com.bandup.api.identity.domain.session.entity.RefreshToken;
import com.bandup.api.identity.domain.session.repository.SessionRepository;
import com.bandup.api.identity.domain.user.aggregate.User;

/**
 * Builds the signed-in result shared by {@link Login} (a new session) and
 * {@link RefreshSession} (a rotated session): a fresh access token plus the
 * refresh token for the cookie.
 */
@Service
public class SessionIssuer {

    private final SessionRepository sessions;
    private final AccessTokenIssuer accessTokens;
    private final TokenGenerator tokenGenerator;
    private final AuthSettings settings;

    public SessionIssuer(SessionRepository sessions, AccessTokenIssuer accessTokens, TokenGenerator tokenGenerator,
            AuthSettings settings) {
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.tokenGenerator = tokenGenerator;
        this.settings = settings;
    }

    /** Starts a new login session and stores it. */
    public SignedIn start(User user, Instant now) {
        GeneratedToken refresh = tokenGenerator.generate();
        Session session = Session.start(user.id(), refresh.hash(), now, settings.refreshTokenLifetime());
        sessions.save(session);
        return signedIn(user, refresh.raw(), session.newestToken(), now);
    }

    /** The result for a refresh token of a stored session (e.g. just rotated). */
    public SignedIn signedIn(User user, String rawRefreshToken, RefreshToken refreshToken, Instant now) {
        return new SignedIn(accessTokens.issue(user, now), rawRefreshToken, refreshToken.expiresAt(), now,
                UserView.of(user));
    }
}
