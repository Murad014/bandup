package com.bandup.api.vocabulary.domain.word.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A word card's identity ({@code vocab_word.id}), handed out by the repository before the card exists. */
@ValueObject
public record WordId(long value) {

    public WordId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid WordId " + value);
        }
    }
}
