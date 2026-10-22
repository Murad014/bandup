package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The signed-in learner, by the public user id from the access token. Identity's {@code UserId} is never imported. */
@ValueObject
public record LearnerId(UUID value) {

    public LearnerId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
