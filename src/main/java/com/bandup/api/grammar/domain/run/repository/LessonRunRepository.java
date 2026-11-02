package com.bandup.api.grammar.domain.run.repository;

import java.time.Instant;
import java.util.Optional;

import com.bandup.api.grammar.domain.run.aggregate.LessonRun;
import com.bandup.api.grammar.domain.run.valueobject.RunId;

/** Loads and stores lesson runs. Implemented in infrastructure. */
public interface LessonRunRepository {

    /** The run, locked for the rest of the transaction (two answers at once must not both finish it). */
    Optional<LessonRun> findById(RunId id);

    void save(LessonRun run);

    /** Deletes unfinished runs started before {@code cutoff} (grammar requirement 8.4). @return how many */
    int deleteUnfinishedStartedBefore(Instant cutoff);
}
