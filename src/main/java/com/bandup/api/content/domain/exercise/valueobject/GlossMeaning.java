package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** A glossed word's meaning in one help language, with who wrote it and whether a person checked it. */
@ValueObject
public record GlossMeaning(String text, TextSource source, boolean reviewed) {

    public GlossMeaning {
        Objects.requireNonNull(source, "source");
        if (text == null || text.isBlank()) {
            throw DomainException.of("glosses", "A meaning can't be empty.");
        }
        text = text.strip();
    }
}
