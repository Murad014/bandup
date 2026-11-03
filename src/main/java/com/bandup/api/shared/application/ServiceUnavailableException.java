package com.bandup.api.shared.application;

/** A service the request needs is not available right now, e.g. an outside AI service (503). */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
