package com.bandup.api.grammar.domain.run.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A run's identity: a random UUID, so a guest's run can't be guessed (grammar requirement 8.2). */
@ValueObject
public record RunId(UUID value) {

    public RunId {
        Objects.requireNonNull(value, "value");
    }

    public static RunId random() {
        return new RunId(UUID.randomUUID());
    }

    /** A number derived from the id, e.g. to shuffle tiles the same way on every load. */
    public long seed() {
        return value.getMostSignificantBits() ^ value.getLeastSignificantBits();
    }
}
