package com.bandup.api.vocabulary.domain.session.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * LEARN (up to 5 new words: meet, practise, master), REVIEW (up to 20 due
 * words) or RECALL (up to 10 learned words, Azerbaijani → English, practice
 * only: boxes never move).
 */
@ValueObject
public enum SessionKind {
    LEARN,
    REVIEW,
    RECALL
}
