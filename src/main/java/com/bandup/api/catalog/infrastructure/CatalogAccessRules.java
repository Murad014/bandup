package com.bandup.api.catalog.infrastructure;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.infrastructure.security.ApiAccessRules;

/** Anyone can browse the catalog; a token, if sent, adds the learner's own progress. */
@Component
class CatalogAccessRules implements ApiAccessRules {

    @Override
    public void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules.requestMatchers(HttpMethod.GET, "/api/v1/exercises", "/api/v1/exercises/filters").permitAll();
    }
}
