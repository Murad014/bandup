package com.bandup.api.grammar.domain.review.valueobject;

import java.time.LocalDate;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A card's schedule after an answer.
 *
 * @param changed false when the card was already answered today (requirement 6.5)
 */
@ValueObject
public record ReviewSchedule(Box box, LocalDate dueOn, boolean changed) {

    public ReviewSchedule {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(dueOn, "dueOn");
    }
}
