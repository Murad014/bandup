package com.bandup.api.vocabulary.domain.word.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A word's meaning in simple English and in Azerbaijani (card-format.md). */
@ValueObject
public record Meaning(String en, String az) {

    public Meaning {
        Objects.requireNonNull(en, "en");
        Objects.requireNonNull(az, "az");
    }
}
