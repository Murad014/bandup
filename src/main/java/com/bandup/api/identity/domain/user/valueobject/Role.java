package com.bandup.api.identity.domain.user.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** What a user may do. Admin endpoints accept ADMIN or AUTHOR. */
@ValueObject
public enum Role {
    LEARNER,
    AUTHOR,
    TRANSLATOR,
    ADMIN
}
