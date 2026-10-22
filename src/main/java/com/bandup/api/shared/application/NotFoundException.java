package com.bandup.api.shared.application;

/** The requested thing doesn't exist, or isn't visible to this caller (404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
