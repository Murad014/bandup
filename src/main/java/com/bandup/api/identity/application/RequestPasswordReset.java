package com.bandup.api.identity.application;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.repository.UserRepository;

/**
 * Use case: "forgot password" (requirement 12.3). Emails a reset link to an
 * active account (confirmed or not: opening the link proves the inbox). The
 * caller always gets the same answer.
 */
@Service
@RequiredArgsConstructor
public class RequestPasswordReset {

    private final UserRepository users;
    private final AccountEmails emails;
    private final Clock clock;

    public void request(String rawEmail) {
        Instant now = clock.instant();
        SendConfirmation.parse(rawEmail).flatMap(users::findByEmail)
                .filter(user -> user.status().canAuthenticate())
                .filter(user -> !emails.sentRecently(user, TokenPurpose.PASSWORD_RESET, now))
                .ifPresent(user -> emails.sendPasswordReset(user, now));
    }
}
