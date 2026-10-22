package com.bandup.api.identity.application.port;

import java.time.Instant;

import com.bandup.api.identity.domain.user.aggregate.User;

/** Port: issues the short-lived access token (a signed JWT in infrastructure). */
public interface AccessTokenIssuer {

    AccessToken issue(User user, Instant now);

    /** The token to send as {@code Authorization: Bearer}, and when it stops working. */
    record AccessToken(String value, Instant expiresAt) {
    }
}
