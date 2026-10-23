package com.bandup.api.shared.application;

import java.time.Duration;

import lombok.Getter;

/** Too many attempts; try again after {@link #retryAfter()} (429 with a Retry-After header). */
public class TooManyRequestsException extends RuntimeException {

    @Getter
    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }
}
