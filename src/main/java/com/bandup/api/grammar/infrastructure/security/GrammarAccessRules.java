package com.bandup.api.grammar.infrastructure.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.infrastructure.security.ApiAccessRules;

/**
 * Access rules for the learner grammar API (grammar design §8): the course,
 * lessons and runs work without an account (a token, if sent, is used);
 * the chosen level and the review need one. Admin endpoints are covered by
 * the shared {@code /api/v1/admin/**} rule.
 */
@Component
class GrammarAccessRules implements ApiAccessRules {

    @Override
    public void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules.requestMatchers(HttpMethod.GET, "/api/v1/grammar/levels", "/api/v1/grammar/course",
                        "/api/v1/grammar/lessons/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/grammar/lessons/*/runs", "/api/v1/grammar/runs/*/answers")
                .permitAll()
                .requestMatchers("/api/v1/grammar/me", "/api/v1/grammar/me/**", "/api/v1/grammar/review",
                        "/api/v1/grammar/review/**").authenticated();
    }
}
