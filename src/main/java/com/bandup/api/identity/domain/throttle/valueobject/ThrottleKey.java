package com.bandup.api.identity.domain.throttle.valueobject;

import java.util.Locale;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * What failed attempts are counted for: an email address (as typed, trimmed and
 * lower-cased — it needn't belong to an account, so locking reveals nothing)
 * or a client address.
 */
@ValueObject
public record ThrottleKey(String value) {

    public ThrottleKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 330) {
            throw new IllegalArgumentException("Invalid throttle key");
        }
    }

    public static ThrottleKey email(String typed) {
        return new ThrottleKey("email:" + (typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT)));
    }

    public static ThrottleKey client(String address) {
        return new ThrottleKey("ip:" + address);
    }

    public boolean isEmail() {
        return value.startsWith("email:");
    }
}
