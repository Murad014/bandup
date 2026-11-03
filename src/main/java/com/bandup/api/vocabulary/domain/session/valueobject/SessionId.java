package com.bandup.api.vocabulary.domain.session.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A learn or review session. */
@ValueObject
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId random() {
        return new SessionId(UUID.randomUUID());
    }

    /** A stable number for shuffling this session's options. */
    public long seed() {
        return value.getMostSignificantBits() ^ value.getLeastSignificantBits();
    }
}
