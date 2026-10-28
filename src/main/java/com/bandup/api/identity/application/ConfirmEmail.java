package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.application.port.TokenGenerator;
import com.bandup.api.identity.domain.token.aggregate.AccountToken;
import com.bandup.api.identity.domain.token.repository.AccountTokenRepository;
import com.bandup.api.identity.domain.token.valueobject.LinkHash;
import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.domain.error.Violation;

/** Use case: the confirmation link was opened (requirement 12.2). */
@Service
@RequiredArgsConstructor
public class ConfirmEmail {

    static final String BAD_LINK = "This link is invalid or has expired. Ask for a new one.";

    private final AccountTokenRepository tokens;
    private final TokenGenerator generator;
    private final UserRepository users;
    private final Clock clock;

    /** @throws InvalidRequestException (400) for an unknown, used or expired link */
    @Transactional
    public void confirm(String rawToken) {
        Instant now = clock.instant();
        AccountToken token = AccountLinks.usable(tokens, generator, rawToken, TokenPurpose.EMAIL_VERIFICATION, now);
        User user = users.findById(token.userId()).orElseThrow(AccountLinks::badLink);
        token.use(now);
        user.verifyEmail(now);
        tokens.save(token);
        users.save(user);
    }

    /** Shared by the link use cases. */
    static final class AccountLinks {

        private AccountLinks() {
        }

        static AccountToken usable(AccountTokenRepository tokens, TokenGenerator generator, String raw,
                TokenPurpose purpose, Instant now) {
            if (raw == null || raw.isBlank() || raw.length() > 200) {
                throw badLink();
            }
            return tokens.findByHash(new LinkHash(generator.hash(raw.strip()).value()))
                    .filter(t -> t.purpose() == purpose && t.isUsable(now))
                    .orElseThrow(AccountLinks::badLink);
        }

        static InvalidRequestException badLink() {
            return new InvalidRequestException(BAD_LINK, List.of(new Violation("token", BAD_LINK)));
        }
    }
}
