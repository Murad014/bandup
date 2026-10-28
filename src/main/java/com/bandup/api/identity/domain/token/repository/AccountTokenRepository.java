package com.bandup.api.identity.domain.token.repository;

import java.time.Instant;
import java.util.Optional;

import com.bandup.api.identity.domain.token.aggregate.AccountToken;
import com.bandup.api.identity.domain.token.valueobject.LinkHash;
import com.bandup.api.identity.domain.token.valueobject.TokenPurpose;
import com.bandup.api.identity.domain.user.valueobject.UserId;

/** The emailed one-time links. */
public interface AccountTokenRepository {

    /** Inserts a new link, or records that a stored one was used. */
    void save(AccountToken token);

    Optional<AccountToken> findByHash(LinkHash hash);

    /** When the user's latest link of this kind was issued (to send at most one a minute). */
    Optional<Instant> latestIssuedAt(UserId userId, TokenPurpose purpose);

    /** Marks every still-open link of this kind used, so only a newer one works. */
    void invalidateOpen(UserId userId, TokenPurpose purpose, Instant now);

    /** Deletes links that expired or were used before {@code cutoff}; returns how many. */
    int deleteEndedBefore(Instant cutoff);
}
