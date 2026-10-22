package com.bandup.api.identity.domain.session.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.identity.domain.session.valueobject.TokenHash;
import com.bandup.api.shared.domain.ddd.Entity;

/**
 * One refresh token handed out to a browser: an entity inside a
 * {@link com.bandup.api.identity.domain.session.aggregate.Session}, identified by its hash.
 *
 * <p>Immutable: "replace" and "revoke" return a new copy. Only the
 * {@link com.bandup.api.identity.domain.session.aggregate.Session} swaps that copy into its own token list, so the session's
 * rules can't be bypassed from outside (calling these methods elsewhere
 * changes nothing).
 */
@Entity
public final class RefreshToken {

    /** What a presented token is worth, checked in this order. */
    public enum State {
        /** Logged out, or its session was revoked. */
        REVOKED,
        /** Already used once: presenting it again means someone copied it. */
        REPLACED,
        EXPIRED,
        USABLE
    }

    private final TokenHash hash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final TokenHash replacedBy;
    private final Instant revokedAt;

    private RefreshToken(TokenHash hash, Instant issuedAt, Instant expiresAt, TokenHash replacedBy, Instant revokedAt) {
        this.hash = Objects.requireNonNull(hash, "hash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("A refresh token must expire after it is issued");
        }
        this.replacedBy = replacedBy;
        this.revokedAt = revokedAt;
    }

    /** A new token, valid for {@code lifetime} from {@code now}. */
    public static RefreshToken issue(TokenHash hash, Instant now, Duration lifetime) {
        return new RefreshToken(hash, now, now.plus(lifetime), null, null);
    }

    /** Rebuilds a stored token (for the persistence adapter, via {@link com.bandup.api.identity.domain.session.aggregate.Session#restore}). */
    public static RefreshToken restore(TokenHash hash, Instant issuedAt, Instant expiresAt, TokenHash replacedBy,
            Instant revokedAt) {
        return new RefreshToken(hash, issuedAt, expiresAt, replacedBy, revokedAt);
    }

    public State stateAt(Instant now) {
        if (revokedAt != null) {
            return State.REVOKED;
        }
        if (replacedBy != null) {
            return State.REPLACED;
        }
        if (!now.isBefore(expiresAt)) {
            return State.EXPIRED;
        }
        return State.USABLE;
    }

    public boolean isUsable(Instant now) {
        return stateAt(now) == State.USABLE;
    }

    /** A copy of this token, marked as replaced by {@code next}. */
    public RefreshToken withReplacedBy(TokenHash next) {
        return new RefreshToken(hash, issuedAt, expiresAt, Objects.requireNonNull(next, "next"), revokedAt);
    }

    /** A copy of this token, revoked at {@code now}; already revoked tokens keep their first revocation time. */
    public RefreshToken revoked(Instant now) {
        return revokedAt != null ? this : new RefreshToken(hash, issuedAt, expiresAt, replacedBy, now);
    }

    public TokenHash hash() {
        return hash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<TokenHash> replacedBy() {
        return Optional.ofNullable(replacedBy);
    }

    public Optional<Instant> revokedAt() {
        return Optional.ofNullable(revokedAt);
    }

    /** Entities are equal by identity: the hash. */
    @Override
    public boolean equals(Object other) {
        return other instanceof RefreshToken token && hash.equals(token.hash);
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }
}
