package com.bandup.api.grammar.domain.review.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A review card's identity: a random UUID. */
@ValueObject
public record ReviewCardId(UUID value) {

    public ReviewCardId {
        Objects.requireNonNull(value, "value");
    }

    public static ReviewCardId random() {
        return new ReviewCardId(UUID.randomUUID());
    }
}
