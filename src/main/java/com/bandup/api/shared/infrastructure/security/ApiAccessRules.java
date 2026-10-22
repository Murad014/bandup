package com.bandup.api.shared.infrastructure.security;

import java.util.List;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Extension point: each bounded context declares who may call its own
 * endpoints, so the shared kernel never needs to know any context's URLs.
 * {@link SecurityConfig} applies every context's rules, then denies any other
 * {@code /api/**} path.
 */
public interface ApiAccessRules {

    /** Adds the rules for this context's endpoints. */
    void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules);

    /**
     * Endpoints that ignore the {@code Authorization} header, e.g. sign-in: a
     * browser often still sends an expired access token there, and that must
     * not block the request.
     */
    default List<RequestMatcher> tokenFreeEndpoints() {
        return List.of();
    }
}
