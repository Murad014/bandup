package com.bandup.api.grammar.infrastructure.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One {@code grammar_finished_lesson} row (the lesson id is the map key). */
@Embeddable
class FinishedLessonRow {

    @Column(nullable = false)
    String level;

    @Column(name = "first_finished_on", nullable = false)
    LocalDate firstFinishedOn;

    @Column(name = "times_finished", nullable = false)
    int timesFinished;
}
