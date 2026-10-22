package com.bandup.api.shared.application;

/** The caller isn't (or is no longer) signed in, e.g. an invalid refresh token (401). */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
