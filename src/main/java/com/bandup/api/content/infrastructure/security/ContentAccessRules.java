package com.bandup.api.content.infrastructure.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import com.bandup.api.shared.infrastructure.security.ApiAccessRules;

/** Access rules for Content's endpoints (design §6.4): authoring needs the ADMIN or AUTHOR role. */
@Component
class ContentAccessRules implements ApiAccessRules {

    @Override
    public void authorize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules.requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "AUTHOR");
    }
}
