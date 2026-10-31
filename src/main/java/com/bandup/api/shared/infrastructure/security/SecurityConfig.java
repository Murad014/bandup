package com.bandup.api.shared.infrastructure.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Who may call what (design §6.4). Stateless: every API request is authorised
 * by its access token; there are no server sessions and no CSRF tokens (the
 * refresh cookie is SameSite=Strict and only used on /api/v1/auth/*).
 *
 * <p>The rules themselves come from each bounded context ({@link ApiAccessRules});
 * this class only adds the defaults: any other {@code /api/**} path is denied,
 * pages and static files are public.
 *
 * <p>401/403 from the filters are handed to the MVC exception handling, so they
 * get the same Problem Details body as every other API error.
 */
@Configuration
class SecurityConfig {

    /**
     * What a page may load: only Orbiko's own scripts, styles, images and API,
     * plus Google Fonts. No inline scripts or style attributes, no framing.
     * A second line of defence behind the HTML sanitizer.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, List<ApiAccessRules> contexts,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            exceptionResolver.resolveException(request, response, null, exception);
        };
        AccessDeniedHandler forbidden = (request, response, exception) ->
                exceptionResolver.resolveException(request, response, null, exception);

        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    contexts.forEach(context -> context.authorize(auth));
                    auth.requestMatchers("/api/**").denyAll()
                            .anyRequest().permitAll();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver(contexts))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    /** Reads the bearer token, except on endpoints a context declared token-free. */
    private static BearerTokenResolver bearerTokenResolver(List<ApiAccessRules> contexts) {
        List<RequestMatcher> tokenFree = contexts.stream().flatMap(c -> c.tokenFreeEndpoints().stream()).toList();
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> tokenFree.stream().anyMatch(m -> m.matches(request)) ? null : standard.resolve(request);
    }

    /** The token's {@code roles} claim becomes Spring authorities ({@code ADMIN} → {@code ROLE_ADMIN}). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(AccessTokenClaims.ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
