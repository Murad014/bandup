package com.bandup.api.identity.domain.session.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * The SHA-256 hash of a refresh token, as 64 lower-case hex characters. Only
 * the hash is ever stored; the token itself lives in the browser's cookie.
 * Identifies a refresh token within its session.
 */
@ValueObject
public record TokenHash(String value) {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public TokenHash {
        if (value == null || !SHA256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("A token hash must be 64 lower-case hex characters");
        }
    }

    @Override
    public String toString() {
        return value.substring(0, 8) + "…";
    }
}
