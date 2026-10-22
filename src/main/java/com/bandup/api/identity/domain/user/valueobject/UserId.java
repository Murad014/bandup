package com.bandup.api.identity.domain.user.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A user's identity: the public UUID that appears in the JWT ({@code sub}) and
 * the API. The database's internal numeric id never leaves persistence.
 */
@ValueObject
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
