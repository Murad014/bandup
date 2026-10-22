package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** Why an answer is right, in one help language (sanitized HTML), with its source and review state. */
@ValueObject
public record Explanation(SanitizedHtml html, TextSource source, boolean reviewed) {

    public Explanation {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(source, "source");
    }
}
