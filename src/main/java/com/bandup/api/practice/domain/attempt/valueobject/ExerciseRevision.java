package com.bandup.api.practice.domain.attempt.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The content revision of the exercise that was answered (starts at 1). */
@ValueObject
public record ExerciseRevision(int value) {

    public ExerciseRevision {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid revision " + value);
        }
    }
}
