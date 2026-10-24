package com.bandup.api.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Production must not start with a random signing key (sessions would break on every restart). */
class JwtSecretRequiredTest {

    @Test
    void aMissingSecretStopsTheStartWhenItIsRequired() {
        assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(new JwtProperties("", null, null, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BANDUP_JWT_SECRET is required");
        assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(new JwtProperties("short", null, null, true)))
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void localDevelopmentStillGetsARandomKey() {
        assertThat(new JwtConfig().jwtSigningKey(new JwtProperties(null, null, null, false)).getEncoded()).hasSize(32);
    }
}
