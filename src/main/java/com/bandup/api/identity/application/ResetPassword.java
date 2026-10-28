package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bandup.api.identity.application.port.PasswordHasher;
import com.bandup.api.identity.application.port.TokenGenerator;
import com.bandup.api.identity.domain.session.repository.SessionRepository;
import com.bandup.api.identity.domain.token.aggregate.AccountToken;
import com.bandup.api.identity.domain.token.repository.AccountTokenRepository;
import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;

/**
 * Use case: a new password from the reset link (requirement 12.3). The email
 * counts as confirmed (the link proved the inbox) and every session is signed
 * out. BCrypt runs before the transaction, so it doesn't hold a connection.
 */
@Service
@RequiredArgsConstructor
public class ResetPassword {

    private final AccountTokenRepository tokens;
    private final TokenGenerator generator;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /**
     * @throws com.bandup.api.shared.domain.error.DomainException (422) for a weak password
     * @throws com.bandup.api.shared.application.InvalidRequestException (400) for an unknown, used or expired link
     */
    public void reset(String rawToken, String newPassword) {
        // A bad link is reported before the password, so nobody types a new password for nothing.
        ConfirmEmail.AccountLinks.usable(tokens, generator, rawToken, TokenPurpose.PASSWORD_RESET, clock.instant());
        PasswordHash hash = hasher.hash(NewPassword.of(newPassword));
        transaction.executeWithoutResult(status -> {
            Instant now = clock.instant();
            AccountToken token = ConfirmEmail.AccountLinks.usable(tokens, generator, rawToken, TokenPurpose.PASSWORD_RESET, now);
            User user = users.findById(token.userId()).orElseThrow(ConfirmEmail.AccountLinks::badLink);
            token.use(now);
            user.changePassword(hash);
            user.verifyEmail(now);
            tokens.save(token);
            users.save(user);
            sessions.revokeAllOf(user.id(), now);
        });
    }
}
