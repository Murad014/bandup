package com.bandup.api.vocabulary.domain.word.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A natural phrase with one gap and 4 options; {@code answer} is the index of the right one. */
@ValueObject
public record PairTask(String sentence, List<String> options, int answer) {

    public PairTask {
        Objects.requireNonNull(sentence, "sentence");
        options = List.copyOf(options);
    }
}
