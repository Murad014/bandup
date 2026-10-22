package com.bandup.api.shared.infrastructure.security;

/**
 * The access token's custom claims: the one place their names are defined.
 * Written by Identity's token issuer, read by the security filter (roles) and
 * by {@code CurrentUser} (roles, language). Standard claims ({@code sub},
 * {@code iss}, {@code iat}, {@code exp}) need no constants.
 */
public final class AccessTokenClaims {

    /** The user's role names, e.g. {@code ["ADMIN"]}. */
    public static final String ROLES = "roles";

    /** The user's help language, e.g. {@code "az"}. */
    public static final String LANGUAGE = "lang";

    private AccessTokenClaims() {
    }
}
