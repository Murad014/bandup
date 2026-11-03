package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.util.Objects;
import java.util.UUID;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A learner's saved word. */
@ValueObject
public record SavedWordId(UUID value) {

    public SavedWordId {
        Objects.requireNonNull(value, "value");
    }

    public static SavedWordId random() {
        return new SavedWordId(UUID.randomUUID());
    }
}
