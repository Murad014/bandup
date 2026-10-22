package com.bandup.api.shared.application;

/** The caller is signed in but not allowed to do this (403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
