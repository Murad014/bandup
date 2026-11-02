package com.bandup.api.grammar.domain.run.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The lesson revision a run started on (grammar requirement 8.3). */
@ValueObject
public record LessonRevision(int value) {

    public LessonRevision {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid revision " + value);
        }
    }
}
