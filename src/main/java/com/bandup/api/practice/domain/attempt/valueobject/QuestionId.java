package com.bandup.api.practice.domain.attempt.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A question, by Content's id (kept across edits, so old attempts stay linked). */
@ValueObject
public record QuestionId(long value) {

    public QuestionId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid QuestionId " + value);
        }
    }
}
