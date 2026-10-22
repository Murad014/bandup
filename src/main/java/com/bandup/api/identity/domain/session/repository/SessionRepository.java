package com.bandup.api.identity.domain.session.repository;

import java.util.Optional;

import com.bandup.api.identity.domain.session.aggregate.Session;
import com.bandup.api.identity.domain.session.valueobject.TokenHash;

/** Port: stored sessions (token hashes only), loaded and saved as whole aggregates. */
public interface SessionRepository {

    /**
     * The whole session containing this token. The session is locked until the
     * current transaction ends, so concurrent uses of any of its tokens run one
     * after another. Requires a transaction.
     */
    Optional<Session> findByTokenHash(TokenHash hash);

    /** Inserts new tokens and updates changed ones. */
    void save(Session session);
}
