package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The content revision; attempts record which one they answered. Starts at 1. */
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
