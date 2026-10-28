package com.bandup.api.identity.domain.token.valueobject;

import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The SHA-256 hash of an emailed link's secret (64 lower-case hex characters). Only the hash is stored. */
@ValueObject
public record LinkHash(String value) {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public LinkHash {
        if (value == null || !SHA256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("A link hash must be 64 lower-case hex characters");
        }
    }

    @Override
    public String toString() {
        return value.substring(0, 8) + "…";
    }
}
