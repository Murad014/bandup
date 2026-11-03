package com.bandup.api.vocabulary.domain.session.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** PRACTISE tasks are answered once; MASTER tasks come back until they are right. */
@ValueObject
public enum TaskPhase {
    PRACTISE,
    MASTER
}
