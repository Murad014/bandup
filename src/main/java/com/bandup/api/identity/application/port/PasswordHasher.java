package com.bandup.api.identity.application.port;

import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;

/** Port: one-way password hashing (BCrypt in infrastructure). */
public interface PasswordHasher {

    PasswordHash hash(NewPassword password);

    /** Checks a password typed at login. Login doesn't re-apply the new-password rules. */
    boolean matches(String rawPassword, PasswordHash hash);
}
