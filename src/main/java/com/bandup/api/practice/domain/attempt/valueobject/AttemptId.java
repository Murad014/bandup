package com.bandup.api.practice.domain.attempt.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** An attempt's identity: a UUID created in the domain. */
@ValueObject
public record AttemptId(UUID value) {

    public AttemptId {
        Objects.requireNonNull(value, "value");
    }

    public static AttemptId newId() {
        return new AttemptId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
