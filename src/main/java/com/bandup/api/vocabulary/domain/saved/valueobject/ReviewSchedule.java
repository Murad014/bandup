package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.time.LocalDate;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A word's schedule after a review answer.
 *
 * @param from    the box before the answer
 * @param box     the box after it
 * @param dueOn   the next review day; null once MASTERED
 * @param changed false when the word was already answered today (requirement 6.3)
 */
@ValueObject
public record ReviewSchedule(Box from, Box box, LocalDate dueOn, WordState state, boolean changed) {

    public ReviewSchedule {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(state, "state");
    }
}
