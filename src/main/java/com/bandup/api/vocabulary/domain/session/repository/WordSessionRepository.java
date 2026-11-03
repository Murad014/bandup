package com.bandup.api.vocabulary.domain.session.repository;

import java.time.Instant;
import java.util.Optional;

import com.bandup.api.vocabulary.domain.session.aggregate.WordSession;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionId;

/** Loads and stores sessions whole. Implemented in infrastructure. */
public interface WordSessionRepository {

    /** Loads the session and locks it until the transaction ends. */
    Optional<WordSession> findById(SessionId id);

    void save(WordSession session);

    /** Removes sessions started before the cutoff (finished or not); returns how many. */
    int deleteStartedBefore(Instant cutoff);
}
