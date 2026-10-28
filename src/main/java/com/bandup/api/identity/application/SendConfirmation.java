package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Use case: "send the confirmation link again" (requirement 12.2). The caller
 * always gets the same answer, so it doesn't reveal which emails have an account.
 */
@Service
@RequiredArgsConstructor
public class SendConfirmation {

    private final UserRepository users;
    private final AccountEmails emails;
    private final Clock clock;

    public void resend(String rawEmail) {
        Instant now = clock.instant();
        parse(rawEmail).flatMap(users::findByEmail)
                .filter(User::awaitsEmailConfirmation)
                .filter(user -> !emails.sentRecently(user, TokenPurpose.EMAIL_VERIFICATION, now))
                .ifPresent(user -> emails.sendConfirmation(user, now));
    }

    static Optional<Email> parse(String raw) {
        try {
            return Optional.of(Email.of(raw));
        } catch (DomainException malformed) {
            return Optional.empty();
        }
    }
}
