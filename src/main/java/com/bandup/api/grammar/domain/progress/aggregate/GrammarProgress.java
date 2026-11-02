package com.bandup.api.grammar.domain.progress.aggregate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.FinishedLesson;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.shared.domain.ddd.AggregateRoot;

/**
 * One learner's grammar progress (aggregate root; grammar requirements 1.2,
 * 3, 8.1): the chosen level and every finished lesson with the day it was
 * first finished. Progress in each level is kept when the level changes.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class GrammarProgress {

    /** New lessons a learner may finish per level and day (requirement 3.2). */
    public static final int NEW_LESSONS_PER_DAY = 1;

    @EqualsAndHashCode.Include
    @Getter
    private final LearnerId learner;
    private CourseLevel level;
    private final Map<LessonId, FinishedLesson> finished;

    private GrammarProgress(LearnerId learner, CourseLevel level, Map<LessonId, FinishedLesson> finished) {
        this.learner = Objects.requireNonNull(learner, "learner");
        this.level = level;
        this.finished = new LinkedHashMap<>(finished);
    }

    /** A learner who hasn't chosen a level or finished anything yet. */
    public static GrammarProgress start(LearnerId learner) {
        return new GrammarProgress(learner, null, Map.of());
    }

    public static GrammarProgress restore(LearnerId learner, CourseLevel level, Map<LessonId, FinishedLesson> finished) {
        return new GrammarProgress(learner, level, finished);
    }

    public void chooseLevel(CourseLevel chosen) {
        level = Objects.requireNonNull(chosen, "level");
    }

    /**
     * Records a finished lesson.
     *
     * @return true when it was finished for the first time (a NEW lesson, counted by the daily limit)
     */
    public boolean finish(LessonId lesson, CourseLevel lessonLevel, LocalDate today) {
        FinishedLesson before = finished.get(lesson);
        if (before != null) {
            finished.put(lesson, before.again());
            return false;
        }
        finished.put(lesson, new FinishedLesson(lessonLevel, today, 1));
        return true;
    }

    public boolean hasFinished(LessonId lesson) {
        return finished.containsKey(lesson);
    }

    /** New lessons of {@code courseLevel} first finished on {@code day}. */
    public int newLessonsOn(CourseLevel courseLevel, LocalDate day) {
        return (int) finished.values().stream()
                .filter(f -> f.level() == courseLevel && f.firstFinishedOn().equals(day))
                .count();
    }

    public boolean mayStartNewLesson(CourseLevel courseLevel, LocalDate today) {
        return newLessonsOn(courseLevel, today) < NEW_LESSONS_PER_DAY;
    }

    public Optional<CourseLevel> level() {
        return Optional.ofNullable(level);
    }

    public Map<LessonId, FinishedLesson> finished() {
        return Map.copyOf(finished);
    }
}
