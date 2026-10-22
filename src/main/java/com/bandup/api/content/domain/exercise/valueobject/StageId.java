package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A stage of a course (reference data). */
@ValueObject
public record StageId(long value) {

    public StageId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid StageId " + value);
        }
    }
}
