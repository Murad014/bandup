package com.bandup.api.practice.infrastructure.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.infrastructure.security.ApiAccessRules;

/**
 * Access rules for Practice's endpoints (design §6.4): learners can read
 * passages and submit attempts without an account; a token, if sent, links
 * the attempt to the user.
 */
@Component
class PracticeAccessRules implements ApiAccessRules {

    @Override
    public void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules.requestMatchers(HttpMethod.GET, "/api/v1/passages/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/attempts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/me/**").authenticated(); // "My progress": own attempts only
    }
}
