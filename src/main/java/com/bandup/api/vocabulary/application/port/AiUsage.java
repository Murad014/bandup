package com.bandup.api.vocabulary.application.port;

import java.time.LocalDate;

import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;

/** Port: how many AI cards each learner caused per day (vocabulary requirement 3.8). */
public interface AiUsage {

    int count(LearnerId learner, LocalDate day);

    /** Adds one, and returns the new count. */
    int record(LearnerId learner, LocalDate day);
}
