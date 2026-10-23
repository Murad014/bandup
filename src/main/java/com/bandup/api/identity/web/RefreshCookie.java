package com.bandup.api.identity.web;

import java.time.Duration;
import java.time.Instant;

import lombok.experimental.UtilityClass;

import org.springframework.http.ResponseCookie;

/**
 * The refresh-token cookie (requirement 8.2): HttpOnly (no JavaScript access),
 * Secure, SameSite=Strict, and only sent to {@code /api/v1/auth}.
 */
@UtilityClass
final class RefreshCookie {

    static final String NAME = "bandup_refresh";
    private static final String PATH = "/api/v1/auth";

    /** @param issuedAt when the token was issued; the cookie lives until {@code expiresAt} */
    static String issue(String rawToken, Instant issuedAt, Instant expiresAt) {
        Duration maxAge = Duration.between(issuedAt, expiresAt);
        return base(rawToken).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build().toString();
    }

    static String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Strict").path(PATH);
    }
}
