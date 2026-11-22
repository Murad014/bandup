package com.bandup.api.vocabulary.domain.session.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * LEARN (up to 5 new words: meet, practise, master) or ROUND (the current
 * set's words not mastered yet, Azerbaijani → English; vocabulary
 * requirement 6).
 */
@ValueObject
public enum SessionKind {
    LEARN,
    ROUND
}
