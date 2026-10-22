package com.bandup.api.identity.web;

import java.time.Duration;

import com.bandup.api.identity.application.SignedIn;
import com.bandup.api.identity.application.UserView;

/**
 * Body of a successful login or refresh. The refresh token is NOT here: it's
 * only in the HttpOnly cookie, out of JavaScript's reach.
 *
 * @param expiresIn seconds until the access token expires
 */
record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {

    static AuthResponse of(SignedIn signedIn) {
        long expiresIn = Math.max(0, Duration.between(signedIn.issuedAt(), signedIn.accessToken().expiresAt()).toSeconds());
        return new AuthResponse(signedIn.accessToken().value(), "Bearer", expiresIn, signedIn.user());
    }
}
