package com.bandup.api.vocabulary.infrastructure.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.infrastructure.security.ApiAccessRules;

/**
 * Access rules for the learner vocabulary API (design §8): the word bank
 * lookup is public; saved words and sessions need a token. Admin endpoints are
 * covered by the shared {@code /api/v1/admin/**} rule.
 */
@Component
class VocabularyAccessRules implements ApiAccessRules {

    @Override
    public void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules.requestMatchers(HttpMethod.GET, "/api/v1/vocabulary/lookup").permitAll()
                .requestMatchers("/api/v1/vocabulary/words", "/api/v1/vocabulary/words/**",
                        "/api/v1/vocabulary/sessions", "/api/v1/vocabulary/sessions/**").authenticated();
    }
}
