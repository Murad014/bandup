package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** One choice: its key and its label (target language), e.g. {@code A — Fewer elk}. */
@ValueObject
public record Option(OptionKey key, String label) {

    public Option {
        Objects.requireNonNull(key, "key");
        label = label == null ? "" : label.strip();
    }
}
