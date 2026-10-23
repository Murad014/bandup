package com.bandup.api.shared.application;

import java.time.Duration;

/** Too many attempts; try again after {@link #retryAfter()} (429 with a Retry-After header). */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
