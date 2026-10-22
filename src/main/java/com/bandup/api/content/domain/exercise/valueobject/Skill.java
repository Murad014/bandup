package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The skill an exercise practises. */
@ValueObject
public enum Skill {
    READING,
    LISTENING,
    WRITING,
    SPEAKING,
    GRAMMAR
}
