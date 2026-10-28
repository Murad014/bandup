package com.bandup.api.shared.application;

import java.util.Optional;

/** The caller is signed in but not allowed to do this (403). */
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String message) {
        this(message, null);
    }

    /** @param code a stable reason the page can act on (sent as {@code "code"}), e.g. {@code EMAIL_NOT_VERIFIED} */
    public ForbiddenException(String message, String code) {
        super(message);
        this.code = code;
    }

    public Optional<String> code() {
        return Optional.ofNullable(code);
    }
}
