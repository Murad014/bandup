package com.bandup.api.vocabulary.domain.word.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * PREPARING (the AI is writing it), READY (learners can learn it), FAILED (the
 * AI didn't give a valid card; "Try again"), REJECTED (not an English word we
 * teach; the text never calls the AI again).
 */
@ValueObject
public enum CardStatus {
    PREPARING,
    READY,
    FAILED,
    REJECTED
}
