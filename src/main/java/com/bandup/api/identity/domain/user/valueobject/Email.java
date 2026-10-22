package com.bandup.api.identity.domain.user.valueobject;

import java.util.Locale;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** An email address, trimmed and stored lower-case (the database requires it). */
@ValueObject
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public Email {
        if (value == null || !value.equals(value.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Use Email.of to normalise the address first");
        }
    }

    /** @throws DomainException if the address is missing, too long or not email-shaped */
    public static Email of(String raw) {
        String normalised = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw DomainException.of("email", "Email is required.");
        }
        if (normalised.length() > MAX_LENGTH || !SHAPE.matcher(normalised).matches()) {
            throw DomainException.of("email", "Enter a valid email address.");
        }
        return new Email(normalised);
    }

    @Override
    public String toString() {
        return value;
    }
}
