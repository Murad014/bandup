package com.bandup.api.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.bandup.api.identity.application.port.AccessTokenIssuer.AccessToken;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.identity.domain.user.valueobject.UserStatus;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.infrastructure.security.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** The access token carries exactly the claims of design §6.3. */
class JwtAccessTokenIssuerTest {

    private static final SecretKey KEY = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(), "HmacSHA256");

    @Test
    void containsPublicIdRolesLanguageIssuerAndA15MinuteExpiry() {
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                new JwtProperties(null, null, null, false));
        User leyla = User.restore(UserId.newId(), Email.of("leyla@example.com"), new PasswordHash("$2a$12$x"), "Leyla",
                LanguageCode.of("az"), LanguageCode.of("tr"), LanguageCode.ENGLISH,
                EnumSet.of(Role.TRANSLATOR, Role.AUTHOR), UserStatus.ACTIVE);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        AccessToken token = issuer.issue(leyla, now);
        Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(token.value());

        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo(leyla.id().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("AUTHOR", "TRANSLATOR");
        assertThat(jwt.getClaimAsString("lang")).isEqualTo("tr");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("bandup");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15))).isEqualTo(token.expiresAt());
        // Never the email or anything private.
        assertThat(jwt.getClaims()).doesNotContainKeys("email", "id", "password");
        assertThat(token.value()).doesNotContain("leyla");
    }
}
