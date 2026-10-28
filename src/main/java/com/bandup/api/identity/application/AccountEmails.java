package com.bandup.api.identity.application;

import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.port.AuthSettings;
import com.bandup.api.identity.application.port.Mailer;
import com.bandup.api.identity.application.port.TokenGenerator;
import com.bandup.api.identity.domain.token.aggregate.AccountToken;
import com.bandup.api.identity.domain.token.repository.AccountTokenRepository;
import com.bandup.api.identity.domain.token.valueobject.LinkHash;
import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.aggregate.User;

/**
 * Issues the one-time links and sends the account emails (requirement 12).
 * A new link makes older open ones of the same kind unusable. Sending happens
 * after the database change; the mailer logs a failure, and the user can ask again.
 */
@Component
@RequiredArgsConstructor
class AccountEmails {

    /** At most one email of a kind per address in this time. */
    static final Duration COOLDOWN = Duration.ofMinutes(1);

    private final AccountTokenRepository tokens;
    private final TokenGenerator generator;
    private final Mailer mailer;
    private final AuthSettings settings;

    /** True if a link of this kind went to the user less than a minute ago. */
    boolean sentRecently(User user, TokenPurpose purpose, Instant now) {
        return tokens.latestIssuedAt(user.id(), purpose).map(at -> at.isAfter(now.minus(COOLDOWN))).orElse(false);
    }

    void sendConfirmation(User user, Instant now) {
        String link = settings.publicUrl() + "/verify-email?token=" + issue(user, TokenPurpose.EMAIL_VERIFICATION, now);
        send(user, "Confirm your BandUp email", greeting(user)
                + "Please confirm your email address to start using BandUp:\n\n" + link + "\n\n"
                + "The link works for 24 hours. If you didn't create a BandUp account, you can ignore this email.\n");
    }

    void sendPasswordReset(User user, Instant now) {
        String link = settings.publicUrl() + "/reset-password?token=" + issue(user, TokenPurpose.PASSWORD_RESET, now);
        send(user, "Reset your BandUp password", greeting(user)
                + "Someone (hopefully you) asked to reset your BandUp password. Choose a new one here:\n\n" + link + "\n\n"
                + "The link works for 1 hour. If you didn't ask for this, ignore this email: your password stays the same.\n");
    }

    void sendInstructorApproved(User user) {
        send(user, "You're now a BandUp Instructor", greeting(user)
                + "Your Instructor request was approved. Sign in and open " + settings.publicUrl() + "/admin "
                + "to create reading passages.\n");
    }

    private String issue(User user, TokenPurpose purpose, Instant now) {
        tokens.invalidateOpen(user.id(), purpose, now);
        TokenGenerator.GeneratedToken token = generator.generate();
        tokens.save(AccountToken.issue(user.id(), purpose, new LinkHash(token.hash().value()), now));
        return token.raw();
    }

    private void send(User user, String subject, String text) {
        mailer.send(new Mailer.Message(user.email().value(), subject, text));
    }

    private static String greeting(User user) {
        return "Hello" + user.displayName().map(name -> " " + name).orElse("") + ",\n\n";
    }
}
