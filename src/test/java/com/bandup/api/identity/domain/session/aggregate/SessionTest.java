package com.bandup.api.identity.domain.session.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.identity.domain.session.aggregate.Session.Reason;
import com.bandup.api.identity.domain.session.aggregate.Session.Rejected;
import com.bandup.api.identity.domain.session.aggregate.Session.Rotated;
import com.bandup.api.identity.domain.session.entity.RefreshToken;
import com.bandup.api.identity.domain.session.valueobject.SessionId;
import com.bandup.api.identity.domain.session.valueobject.TokenHash;
import com.bandup.api.identity.domain.user.valueobject.UserId;

/** The Session aggregate: its rules (requirements 8.4, 8.5) and its invariants. */
class SessionTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
    private static final Duration LIFETIME = Duration.ofDays(14);
    private static final UserId AYSEL = UserId.newId();
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(30));

    @Test
    void aNewSessionHasOneUsableToken() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);

        assertThat(session.userId()).isEqualTo(AYSEL);
        assertThat(session.tokens()).extracting(RefreshToken::hash).containsExactly(hash('a'));
        assertThat(session.newestToken().isUsable(NOW)).isTrue();
        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    void presentingAUsableTokenRotatesIt() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);

        Rotated rotated = (Rotated) session.present(hash('a'), hash('b'), LATER, LIFETIME);

        RefreshToken first = session.token(hash('a')).orElseThrow();
        assertThat(first.stateAt(LATER)).isEqualTo(RefreshToken.State.REPLACED);
        assertThat(first.replacedBy()).contains(hash('b'));
        assertThat(rotated.next().hash()).isEqualTo(hash('b'));
        assertThat(rotated.next().isUsable(LATER)).isTrue();
        assertThat(rotated.next().expiresAt()).isEqualTo(LATER.plus(LIFETIME));
        assertThat(session.newestToken()).isEqualTo(rotated.next());
        assertThat(session.tokens()).extracting(RefreshToken::hash).containsExactly(hash('a'), hash('b'));
    }

    @Test
    void reusingAReplacedTokenRevokesTheWholeSession() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);
        session.present(hash('a'), hash('b'), LATER, LIFETIME);
        Instant theft = LATER.plus(Duration.ofMinutes(5));

        var outcome = session.present(hash('a'), hash('c'), theft, LIFETIME); // the copied first token again

        assertThat(outcome).isEqualTo(new Rejected(Reason.REUSED));
        assertThat(session.isRevoked()).isTrue();
        assertThat(session.token(hash('b')).orElseThrow().revokedAt()).contains(theft); // the real user's token too
        assertThat(session.token(hash('c'))).isEmpty();
    }

    @Test
    void anExpiredTokenIsRejectedAndNothingChanges() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);

        assertThat(session.present(hash('a'), hash('b'), NOW.plus(LIFETIME), LIFETIME)).isEqualTo(new Rejected(Reason.EXPIRED));
        assertThat(session.tokens()).hasSize(1);
        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    void aLoggedOutSessionRejectsItsTokens() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);
        session.revoke(NOW);

        assertThat(session.isRevoked()).isTrue();
        assertThat(session.present(hash('a'), hash('b'), LATER, LIFETIME)).isEqualTo(new Rejected(Reason.REVOKED));
        assertThat(session.tokens()).hasSize(1);
    }

    @Test
    void reuseAfterTheSessionWasAlreadyRevokedIsJustRejected() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);
        session.present(hash('a'), hash('b'), LATER, LIFETIME);
        session.present(hash('a'), hash('c'), LATER, LIFETIME); // theft → revoked

        assertThat(session.present(hash('a'), hash('d'), LATER.plusSeconds(1), LIFETIME)).isEqualTo(new Rejected(Reason.REVOKED));
    }

    @Test
    void revokingKeepsTheFirstRevocationTime() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);
        session.revoke(LATER);
        session.revoke(LATER.plusSeconds(5));

        assertThat(session.newestToken().revokedAt()).contains(LATER);
    }

    @Test
    void aTokenFromAnotherSessionCannotBePresented() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);
        assertThatThrownBy(() -> session.present(hash('z'), hash('b'), LATER, LIFETIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------- invariants

    @Test
    void aStoredSessionMustBeConsistent() {
        SessionId id = SessionId.newId();
        RefreshToken a = RefreshToken.restore(hash('a'), NOW, NOW.plus(LIFETIME), null, null);

        assertThatThrownBy(() -> Session.restore(id, AYSEL, List.of()))
                .as("no tokens").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Session.restore(id, AYSEL, List.of(a, RefreshToken.restore(hash('a'), LATER, LATER.plus(LIFETIME), null, null))))
                .as("same token twice").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Session.restore(id, AYSEL, List.of(RefreshToken.restore(hash('a'), NOW, NOW.plus(LIFETIME), hash('x'), null))))
                .as("replaced by a token outside the session").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokensCannotBeChangedFromOutside() {
        Session session = Session.start(AYSEL, hash('a'), NOW, LIFETIME);
        assertThatThrownBy(() -> session.tokens().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isEqualByIdentity() {
        SessionId id = SessionId.newId();
        RefreshToken a = RefreshToken.restore(hash('a'), NOW, NOW.plus(LIFETIME), null, null);
        RefreshToken b = RefreshToken.restore(hash('b'), NOW, NOW.plus(LIFETIME), null, null);

        assertThat(Session.restore(id, AYSEL, List.of(a))).isEqualTo(Session.restore(id, AYSEL, List.of(b)));
        assertThat(Session.start(AYSEL, hash('a'), NOW, LIFETIME)).isNotEqualTo(Session.start(AYSEL, hash('a'), NOW, LIFETIME));
    }

    private static TokenHash hash(char c) {
        return new TokenHash(String.valueOf(c).repeat(64));
    }
}
