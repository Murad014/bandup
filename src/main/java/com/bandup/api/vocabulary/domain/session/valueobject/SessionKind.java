package com.bandup.api.vocabulary.domain.session.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** LEARN (up to 5 new words: meet, practise, master) or REVIEW (up to 20 due words). */
@ValueObject
public enum SessionKind {
    LEARN,
    REVIEW
}
