package com.bandup.api.shared.application;

/**
 * The request conflicts with the current state, e.g. a save based on a stale
 * version or an email that's already registered (409).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
