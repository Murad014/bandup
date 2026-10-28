package com.bandup.api.identity.domain.token.aggregate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import lombok.Getter;

import com.bandup.api.identity.domain.token.valueobject.LinkHash;
import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.shared.domain.ddd.AggregateRoot;

/**
 * A one-time link sent by email: confirm an email address, or reset a
 * password (requirement 12). Only the hash of the link's secret is kept. It
 * works once, until it expires; a newer link of the same kind makes it unusable
 * (the repository marks it used).
 */
@AggregateRoot
@Getter
public final class AccountToken {

    private final UserId userId;
    private final TokenPurpose purpose;
    private final LinkHash hash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private AccountToken(UserId userId, TokenPurpose purpose, LinkHash hash, Instant issuedAt, Instant expiresAt,
            Instant usedAt) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.hash = Objects.requireNonNull(hash, "hash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("A link must expire after it is issued");
        }
        this.usedAt = usedAt;
    }

    /** A new link for {@code purpose}, valid for the purpose's lifetime from {@code now}. */
    public static AccountToken issue(UserId userId, TokenPurpose purpose, LinkHash hash, Instant now) {
        return new AccountToken(userId, purpose, hash, now, now.plus(purpose.lifetime()), null);
    }

    /** Rebuilds a stored link (for the persistence adapter). */
    public static AccountToken restore(UserId userId, TokenPurpose purpose, LinkHash hash, Instant issuedAt,
            Instant expiresAt, Instant usedAt) {
        return new AccountToken(userId, purpose, hash, issuedAt, expiresAt, usedAt);
    }

    /** Not used yet and not expired. */
    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Uses the link (once).
     *
     * @throws IllegalStateException if it was used already or has expired
     */
    public void use(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("This link was used already or has expired");
        }
        usedAt = now;
    }

    public Optional<Instant> usedAt() {
        return Optional.ofNullable(usedAt);
    }
}
