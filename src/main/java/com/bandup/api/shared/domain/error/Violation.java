package com.bandup.api.shared.domain.error;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One broken rule, anchored to the field it concerns (e.g. {@code "title"},
 * {@code "answerKey[4].answer"}). The web layer turns these into the
 * {@code errors} list of a 422 response.
 *
 * @param field   the field path; empty when the rule concerns the whole object
 * @param message what is wrong, for a person to read
 */
@ValueObject
public record Violation(String field, String message) {

    public Violation {
        Objects.requireNonNull(field, "field");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A violation needs a message");
        }
    }
}
