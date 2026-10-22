package com.bandup.api.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.bandup.api.identity.application.port.TokenGenerator.GeneratedToken;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;

/** BCrypt and refresh-token generation, without Spring. */
class CryptoAdaptersTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();
    private final SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void bcryptMatchesOnlyTheRightPassword() {
        PasswordHash hash = hasher.hash(NewPassword.of("correct horse battery"));

        assertThat(hash.value()).startsWith("$2a$12$");
        assertThat(hasher.matches("correct horse battery", hash)).isTrue();
        assertThat(hasher.matches("Correct horse battery", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
    }

    @Test
    void theSamePasswordHashesDifferentlyEachTime() {
        assertThat(hasher.hash(NewPassword.of("correct horse battery")))
                .isNotEqualTo(hasher.hash(NewPassword.of("correct horse battery")));
    }

    @Test
    void refreshTokenHashIsSha256HexOfTheCookieValue() {
        // Known SHA-256 test vector for "abc".
        assertThat(generator.hash("abc").value())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void generatedTokensAreUrlSafeUniqueAndHashedConsistently() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            GeneratedToken token = generator.generate();
            assertThat(token.raw()).matches("[A-Za-z0-9_-]{43}"); // 32 bytes, base64url, no padding
            assertThat(token.hash()).isEqualTo(generator.hash(token.raw()));
            assertThat(seen.add(token.raw())).isTrue();
        }
        assertThat(generator.generate().toString()).doesNotContain("raw");
    }
}
