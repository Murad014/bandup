package com.bandup.api.practice.application.port;

import java.util.Optional;

import com.bandup.api.practice.domain.attempt.valueobject.GradableExercise;

/**
 * Port: a PUBLISHED exercise as Practice needs it for grading (questions in
 * order, answers, evidence keys, explanations in every language). Read from
 * Content's tables with SQL; Content's classes are never used.
 */
public interface GradableExercises {

    /** Empty if the exercise doesn't exist or isn't PUBLISHED. */
    Optional<GradableExercise> findPublished(String slug);
}
