package com.bandup.api.shared.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Signs and verifies access tokens with HS256. The key comes from
 * {@code BANDUP_JWT_SECRET} and is never committed. Without it (local dev) a
 * random key is generated at startup: everything works, but tokens stop working
 * after a restart.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] secret;
        if (properties.secret() == null || properties.secret().isBlank()) {
            log.warn("BANDUP_JWT_SECRET is not set: using a random signing key. Access tokens won't survive a restart. "
                    + "Set BANDUP_JWT_SECRET (at least {} bytes) in every shared or deployed environment.", MIN_SECRET_BYTES);
            secret = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(secret);
        } else {
            secret = properties.secret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("BANDUP_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
            }
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /** Checks the signature, expiry (with Spring's default 60 s clock skew) and issuer. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
