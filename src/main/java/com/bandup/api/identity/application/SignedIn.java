package com.bandup.api.identity.application;

import java.time.Instant;

import com.bandup.api.identity.application.port.AccessTokenIssuer.AccessToken;

/**
 * The result of signing in or refreshing: a new access token for the
 * {@code Authorization} header and a new refresh token for the cookie.
 *
 * @param refreshToken          the raw refresh token, only for the cookie
 * @param refreshTokenExpiresAt when the cookie should expire
 * @param issuedAt              when both were issued; lifetimes are measured from here
 */
public record SignedIn(AccessToken accessToken, String refreshToken, Instant refreshTokenExpiresAt, Instant issuedAt,
        UserView user) {

    @Override
    public String toString() {
        return "SignedIn[user=" + user.id() + "]";
    }
}
