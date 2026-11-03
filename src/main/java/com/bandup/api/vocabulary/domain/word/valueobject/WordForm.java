package com.bandup.api.vocabulary.domain.word.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One normalised spelling of a word ("riverbanks"): lower case, trimmed,
 * single spaces, straight apostrophes. Each form belongs to at most one card.
 * Made by {@code WordNormaliser} / {@code WordGate}, which check the rules.
 */
@ValueObject
public record WordForm(String value) {

    public WordForm {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 40) {
            throw new IllegalArgumentException("A word form is 1 to 40 characters: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
