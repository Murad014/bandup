package com.bandup.api.identity.web;

import java.time.Duration;
import java.time.Instant;

import lombok.experimental.UtilityClass;

import org.springframework.http.ResponseCookie;

/**
 * The refresh-token cookie (requirement 8.2): HttpOnly (no JavaScript access),
 * Secure, SameSite=Strict, and only sent to {@code /api/v1/auth}.
 *
 * <p>{@code secure} is false only in the {@code local} profile: Safari drops
 * Secure cookies on http://localhost, which signed admins out on every page load.
 */
@UtilityClass
final class RefreshCookie {

    static final String NAME = "bandup_refresh";
    private static final String PATH = "/api/v1/auth";

    /** @param issuedAt when the token was issued; the cookie lives until {@code expiresAt} */
    static String issue(String rawToken, Instant issuedAt, Instant expiresAt, boolean secure) {
        Duration maxAge = Duration.between(issuedAt, expiresAt);
        return base(rawToken, secure).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build().toString();
    }

    static String clear(boolean secure) {
        return base("", secure).maxAge(Duration.ZERO).build().toString();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
    }
}
