package com.bandup.api.identity.domain.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.bandup.api.identity.domain.session.valueobject.TokenHash;

/** The entity inside a session: its states and identity. */
class RefreshTokenTest {

    static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
    static final Duration LIFETIME = Duration.ofDays(14);

    @Test
    void aNewTokenIsUsableUntilItExpires() {
        RefreshToken token = RefreshToken.issue(hash('a'), NOW, LIFETIME);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(LIFETIME));
        assertThat(token.stateAt(NOW)).isEqualTo(RefreshToken.State.USABLE);
        assertThat(token.stateAt(NOW.plus(LIFETIME).minusSeconds(1))).isEqualTo(RefreshToken.State.USABLE);
        assertThat(token.stateAt(NOW.plus(LIFETIME))).isEqualTo(RefreshToken.State.EXPIRED);
    }

    @Test
    void revokedComesBeforeEverythingElse() {
        RefreshToken token = RefreshToken.restore(hash('a'), NOW, NOW.plus(LIFETIME), hash('b'), NOW.plusSeconds(60));
        assertThat(token.stateAt(NOW.plus(Duration.ofDays(30)))).isEqualTo(RefreshToken.State.REVOKED);
    }

    @Test
    void aReplacedTokenStaysReplacedEvenAfterItExpires() {
        // A copied old token is still a theft signal, even if it's old.
        RefreshToken token = RefreshToken.restore(hash('a'), NOW, NOW.plus(LIFETIME), hash('b'), null);
        assertThat(token.stateAt(NOW.plus(Duration.ofDays(30)))).isEqualTo(RefreshToken.State.REPLACED);
    }

    @Test
    void mustExpireAfterItIsIssued() {
        assertThatThrownBy(() -> RefreshToken.restore(hash('a'), NOW, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacingAndRevokingReturnCopiesAndNeverChangeTheOriginal() {
        RefreshToken original = RefreshToken.issue(hash('a'), NOW, LIFETIME);

        RefreshToken replaced = original.withReplacedBy(hash('b'));
        RefreshToken revoked = original.revoked(NOW.plusSeconds(60));

        assertThat(original.replacedBy()).isEmpty();
        assertThat(original.revokedAt()).isEmpty();
        assertThat(original.isUsable(NOW)).isTrue();
        assertThat(replaced.replacedBy()).contains(hash('b'));
        assertThat(revoked.revokedAt()).contains(NOW.plusSeconds(60));
        assertThat(revoked.revoked(NOW.plusSeconds(90)).revokedAt()).contains(NOW.plusSeconds(60)); // first time kept
    }

    @Test
    void isEqualByItsHash() {
        assertThat(RefreshToken.restore(hash('a'), NOW, NOW.plus(LIFETIME), null, null))
                .isEqualTo(RefreshToken.restore(hash('a'), NOW.minusSeconds(5), NOW.plus(LIFETIME), hash('b'), NOW))
                .isNotEqualTo(RefreshToken.issue(hash('b'), NOW, LIFETIME));
    }

    @Test
    void tokenHashMustBeLowerCaseSha256Hex() {
        assertThat(hash('a').value()).hasSize(64);
        assertThatThrownBy(() -> new TokenHash("A".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenHash("a".repeat(63))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenHash("not-a-hash")).isInstanceOf(IllegalArgumentException.class);
        assertThat(hash('a').toString()).doesNotContain("a".repeat(64));
    }

    static TokenHash hash(char c) {
        return new TokenHash(String.valueOf(c).repeat(64));
    }
}
