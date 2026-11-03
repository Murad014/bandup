package com.bandup.api.vocabulary.domain.word.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A sentence with one {@code ___} gap and the form of the word that fills it. */
@ValueObject
public record GapTask(String sentence, String answer) {

    public GapTask {
        Objects.requireNonNull(sentence, "sentence");
        Objects.requireNonNull(answer, "answer");
    }
}
