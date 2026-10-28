package com.bandup.api.identity.domain.token.valueobject;

import java.time.Duration;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** What an emailed link is for, and how long it works (requirement 12). */
@ValueObject
public enum TokenPurpose {
    EMAIL_VERIFICATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration lifetime;

    TokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
