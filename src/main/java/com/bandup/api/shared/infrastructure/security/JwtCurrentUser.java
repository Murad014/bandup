package com.bandup.api.shared.infrastructure.security;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageCode;

/** {@link CurrentUser} from the validated access token of the current request. */
@Component
class JwtCurrentUser implements CurrentUser {

    @Override
    public Optional<Caller> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        List<String> roles = jwt.getClaimAsStringList(AccessTokenClaims.ROLES);
        return Optional.of(new Caller(
                UUID.fromString(jwt.getSubject()),
                roles == null ? Set.of() : new HashSet<>(roles),
                language(jwt.getClaimAsString(AccessTokenClaims.LANGUAGE))));
    }

    private static Optional<LanguageCode> language(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LanguageCode.of(code));
        } catch (DomainException malformed) {
            return Optional.empty();
        }
    }
}
