package com.bandup.api.shared.application;

import java.util.List;

import com.bandup.api.shared.domain.error.Violation;

/**
 * The request is malformed for this use case, e.g. an unknown or repeated
 * question number (400). May list every problem found ({@code errors}, same
 * shape as a 422). Business-rule violations use
 * {@link com.bandup.api.shared.domain.error.DomainException} (422) instead.
 */
public class InvalidRequestException extends RuntimeException {

    private final List<Violation> errors;

    public InvalidRequestException(String message) {
        this(message, List.of());
    }

    public InvalidRequestException(String message, List<Violation> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    /** Each problem, anchored to its field; empty when the message says it all. */
    public List<Violation> errors() {
        return errors;
    }
}
