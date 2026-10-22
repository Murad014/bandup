package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** An image or audio file's metadata row (reference data). */
@ValueObject
public record MediaId(long value) {

    public MediaId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid MediaId " + value);
        }
    }
}
