package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** Who wrote a piece of help text: a person or a machine translation. */
@ValueObject
public enum TextSource {
    HUMAN,
    MACHINE
}
