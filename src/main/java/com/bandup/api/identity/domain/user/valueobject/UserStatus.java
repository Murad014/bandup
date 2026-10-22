package com.bandup.api.identity.domain.user.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** Only active users can sign in or refresh their session. */
@ValueObject
public enum UserStatus {
    ACTIVE,
    DISABLED,
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
