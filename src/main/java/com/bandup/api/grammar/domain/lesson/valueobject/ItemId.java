package com.bandup.api.grammar.domain.lesson.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * An item's id inside its lesson: {@code p1, p2, …} for practice, {@code c1,
 * c2, …} for check items. Review cards point at it, so it stays the same
 * across edits. The format is checked by the {@code LessonPolicy}.
 */
@ValueObject
public record ItemId(String value) {

    public ItemId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value;
    }
}
