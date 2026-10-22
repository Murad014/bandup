package com.bandup.api.identity.domain.user.valueobject;

import java.nio.charset.StandardCharsets;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * A password someone chose, before hashing. At least 10 characters; at most
 * 72 bytes, because BCrypt ignores anything longer (two long passwords that
 * differ only after byte 72 would otherwise match). Never printed.
 */
@ValueObject
public final class NewPassword {

    public static final int MIN_CHARACTERS = 10;
    public static final int MAX_BYTES = 72;

    private final String value;

    private NewPassword(String value) {
        this.value = value;
    }

    /** @throws DomainException if the password breaks the length rules */
    public static NewPassword of(String raw) {
        if (raw == null || raw.codePointCount(0, raw.length()) < MIN_CHARACTERS) {
            throw DomainException.of("password", "Password must be at least " + MIN_CHARACTERS + " characters.");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw DomainException.of("password", "Password is too long (at most " + MAX_BYTES + " bytes).");
        }
        return new NewPassword(raw);
    }

    /** The raw password, only for the hasher. */
    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "NewPassword[****]";
    }
}
