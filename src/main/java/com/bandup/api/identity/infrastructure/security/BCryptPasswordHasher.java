package com.bandup.api.identity.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.port.PasswordHasher;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;

/** {@link PasswordHasher} with BCrypt (cost 12). Each hash has its own random salt. */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    @Override
    public PasswordHash hash(NewPassword password) {
        return new PasswordHash(bcrypt.encode(password.reveal()));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return rawPassword != null && bcrypt.matches(rawPassword, hash.value());
    }
}
