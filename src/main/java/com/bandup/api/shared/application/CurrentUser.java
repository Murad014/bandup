package com.bandup.api.shared.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bandup.api.shared.domain.language.LanguageCode;

/**
 * Port: who is calling, read from the request's access token. Empty for
 * anonymous requests (e.g. practising without an account).
 */
public interface CurrentUser {

    Optional<Caller> get();

    /**
     * The signed-in caller.
     *
     * @param userId          the user's public id (the token's subject)
     * @param roles           role names, e.g. {@code ADMIN}, {@code LEARNER}
     * @param supportLanguage the user's help language, if the token has one
     */
    record Caller(UUID userId, Set<String> roles, Optional<LanguageCode> supportLanguage) {

        public Caller {
            roles = Set.copyOf(roles);
        }

        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }
}
