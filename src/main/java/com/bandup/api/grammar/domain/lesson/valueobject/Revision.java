package com.bandup.api.grammar.domain.lesson.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The content revision; a run records the one it started on. Starts at 1, raised by edits of a published lesson. */
@ValueObject
public record Revision(int value) {

    public Revision {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid revision " + value);
        }
    }

    public static Revision first() {
        return new Revision(1);
    }

    public Revision next() {
        return new Revision(value + 1);
    }
}
