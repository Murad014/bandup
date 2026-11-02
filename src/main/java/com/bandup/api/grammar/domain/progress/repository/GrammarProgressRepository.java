package com.bandup.api.grammar.domain.progress.repository;

import java.util.Optional;

import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;

/** Loads and stores a learner's whole grammar progress. Implemented in infrastructure. */
public interface GrammarProgressRepository {

    /** The learner's progress, locked for the rest of the transaction; empty if they never started. */
    Optional<GrammarProgress> findByLearner(LearnerId learner);

    void save(GrammarProgress progress);
}
