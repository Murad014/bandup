package com.bandup.api.shared.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bandup.jwt.*}: how access tokens are signed and how long they live.
 *
 * @param secret              HS256 key, at least 32 bytes ({@code BANDUP_JWT_SECRET}); blank in local dev
 *                            means "generate a random key at startup"
 * @param issuer              the {@code iss} claim
 * @param accessTokenLifetime how long an access token works
 */
@ConfigurationProperties("bandup.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenLifetime) {

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "bandup" : issuer;
        accessTokenLifetime = accessTokenLifetime == null ? Duration.ofMinutes(15) : accessTokenLifetime;
    }
}
