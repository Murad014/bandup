package com.bandup.api.practice.domain.attempt.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** An exercise, by Content's id. Practice's own value object, so it never imports Content. */
@ValueObject
public record ExerciseId(long value) {

    public ExerciseId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid ExerciseId " + value);
        }
    }
}
