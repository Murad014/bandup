package com.bandup.api.grammar.domain.lesson.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** A lesson's place in its level's course: 1, 2, 3, … (no upper limit; the course grows). */
@ValueObject
public record Position(int value) {

    public Position {
        if (value < 1) {
            throw DomainException.of("position", "The position must be 1 or more.");
        }
    }

    public Position next() {
        return new Position(value + 1);
    }
}
