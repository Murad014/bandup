package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A learner: the user's public id. Identity's UserId is never imported. */
@ValueObject
public record LearnerId(UUID value) {

    public LearnerId {
        Objects.requireNonNull(value, "value");
    }
}
