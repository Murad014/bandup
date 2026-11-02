package com.bandup.api.grammar.domain.progress.valueobject;

import java.time.LocalDate;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A lesson the learner has finished: its level, the day it was first finished, and how often. */
@ValueObject
public record FinishedLesson(CourseLevel level, LocalDate firstFinishedOn, int timesFinished) {

    public FinishedLesson {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(firstFinishedOn, "firstFinishedOn");
        if (timesFinished < 1) {
            throw new IllegalArgumentException("A finished lesson was finished at least once");
        }
    }

    public FinishedLesson again() {
        return new FinishedLesson(level, firstFinishedOn, timesFinished + 1);
    }
}
