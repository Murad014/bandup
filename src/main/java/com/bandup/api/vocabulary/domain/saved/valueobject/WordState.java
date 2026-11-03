package com.bandup.api.vocabulary.domain.saved.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** NEW (saved, not learned yet), LEARNING (in the review boxes) or MASTERED (right in box 4). */
@ValueObject
public enum WordState {
    NEW,
    LEARNING,
    MASTERED
}
