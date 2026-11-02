package com.bandup.api.grammar.domain.lesson.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A grammar lesson's identity ({@code grammar_lesson.id}), handed out by the repository before the lesson exists. */
@ValueObject
public record LessonId(long value) {

    public LessonId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid LessonId " + value);
        }
    }
}
