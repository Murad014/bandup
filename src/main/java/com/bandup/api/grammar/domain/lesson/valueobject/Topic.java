package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A lesson's topic tag: lessons with the same key form one topic ("conditionals"). */
@ValueObject
public record Topic(String key, LessonText label) {

    public Topic {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
    }
}
