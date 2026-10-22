package com.bandup.api.identity.infrastructure.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.infrastructure.security.ApiAccessRules;

/** Access rules for Identity's endpoints (design §6.4). */
@Component
class IdentityAccessRules implements ApiAccessRules {

    private static final String[] SIGN_IN = {
            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"};

    @Override
    public void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules.requestMatchers(HttpMethod.POST, SIGN_IN).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated();
    }

    /** Sign-in endpoints ignore a stale access token, so it can't block the refresh that replaces it. */
    @Override
    public List<RequestMatcher> tokenFreeEndpoints() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return Arrays.stream(SIGN_IN).map(path -> (RequestMatcher) paths.matcher(HttpMethod.POST, path)).toList();
    }
}
