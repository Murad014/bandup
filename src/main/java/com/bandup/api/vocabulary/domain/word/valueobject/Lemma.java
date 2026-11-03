package com.bandup.api.vocabulary.domain.word.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A card's base form ("riverbank"); also one of its forms. */
@ValueObject
public record Lemma(String value) {

    public Lemma {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 40) {
            throw new IllegalArgumentException("A lemma is 1 to 40 characters: " + value);
        }
    }

    public WordForm asForm() {
        return new WordForm(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
