package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A question's identity; kept across edits, so recorded attempt answers stay linked to it. */
@ValueObject
public record QuestionId(long value) {

    public QuestionId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid QuestionId " + value);
        }
    }
}
