package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** A mini exercise or a full IELTS-length one. */
@ValueObject
public enum Format {
    MINI,
    FULL
}
