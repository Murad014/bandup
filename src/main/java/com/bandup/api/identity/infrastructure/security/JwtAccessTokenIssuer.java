package com.bandup.api.identity.infrastructure.security;

import java.time.Instant;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.port.AccessTokenIssuer;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.shared.infrastructure.security.AccessTokenClaims;
import com.bandup.api.shared.infrastructure.security.JwtProperties;

/**
 * {@link AccessTokenIssuer}: an HS256 JWT with the claims from design §6.3
 * ({@code sub} = public user id, {@code roles}, {@code lang}, {@code iss},
 * {@code iat}, {@code exp}). Never contains the internal id or the email.
 */
@Component
@RequiredArgsConstructor
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    @Override
    public AccessToken issue(User user, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenLifetime());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AccessTokenClaims.ROLES, user.roles().stream().map(Role::name).sorted().toList())
                .claim(AccessTokenClaims.LANGUAGE, user.supportLanguage().value())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }
}
