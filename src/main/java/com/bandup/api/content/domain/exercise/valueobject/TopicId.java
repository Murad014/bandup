package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A topic used for filtering (reference data). */
@ValueObject
public record TopicId(long value) {

    public TopicId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid TopicId " + value);
        }
    }
}
