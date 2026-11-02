package com.bandup.api.grammar.domain.lesson.repository;

import java.util.Optional;

import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;

/** Loads and stores grammar lessons whole. Implemented in infrastructure. */
public interface GrammarLessonRepository {

    Optional<GrammarLesson> findBySlug(LessonSlug slug);

    Optional<GrammarLesson> findById(LessonId id);

    boolean existsBySlug(LessonSlug slug);

    LessonId nextLessonId();

    /** Whether another PUBLISHED lesson of the level already has this position. */
    boolean positionTaken(GrammarLevel level, Position position, LessonId except);

    /** The highest position among the level's PUBLISHED lessons, if any. */
    Optional<Position> lastPublishedPosition(GrammarLevel level);

    /**
     * Inserts or updates the whole lesson. Fails with a conflict if the stored
     * version isn't the one the lesson was loaded with.
     *
     * @return the new stored version (0 for a new lesson, then +1 per save)
     */
    long save(GrammarLesson lesson);

    void delete(GrammarLesson lesson);
}
