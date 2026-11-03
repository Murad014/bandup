package com.bandup.api.vocabulary.domain.word.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** Who wrote the card's content last: the AI or an admin. */
@ValueObject
public enum CardSource {
    AI,
    ADMIN
}
