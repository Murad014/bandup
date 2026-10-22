package com.bandup.api.identity.domain.session.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A login session's identity (stored as the tokens' {@code family_id}). */
@ValueObject
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId newId() {
        return new SessionId(UUID.randomUUID());
    }
}
