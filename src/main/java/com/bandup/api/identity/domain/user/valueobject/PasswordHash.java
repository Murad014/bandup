package com.bandup.api.identity.domain.user.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * An opaque password hash (BCrypt, produced by the hasher adapter). The domain
 * never sees a raw password again once it's hashed. Never printed in full.
 */
@ValueObject
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A password hash can't be blank");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[****]";
    }
}
