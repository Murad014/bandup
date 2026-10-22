package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A question group's identity; kept across edits. */
@ValueObject
public record GroupId(long value) {

    public GroupId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid GroupId " + value);
        }
    }
}
