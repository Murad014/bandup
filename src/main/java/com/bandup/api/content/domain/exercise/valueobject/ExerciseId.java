package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** An exercise's identity ({@code exercise.id}), handed out by the repository before the exercise exists. */
@ValueObject
public record ExerciseId(long value) {

    public ExerciseId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid ExerciseId " + value);
        }
    }
}
