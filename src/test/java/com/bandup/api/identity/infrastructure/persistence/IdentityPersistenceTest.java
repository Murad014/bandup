package com.bandup.api.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.identity.domain.session.aggregate.Session;
import com.bandup.api.identity.domain.session.entity.RefreshToken;
import com.bandup.api.identity.domain.session.valueobject.TokenHash;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.identity.domain.user.valueobject.UserStatus;
import com.bandup.api.shared.domain.language.LanguageCode;

/** The identity adapters against the real schema (Hibernate validates every entity). */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({TestcontainersConfiguration.class, JpaUserRepository.class, JpaSessionRepository.class,
        IdentityPersistenceTest.FixedClock.class})
class IdentityPersistenceTest {

    static final Instant CLOCK_TIME = Instant.parse("2026-10-22T09:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(CLOCK_TIME, ZoneOffset.UTC);
        }
    }

    private static final LanguageCode AZ = LanguageCode.of("az");
    private static final PasswordHash HASH = new PasswordHash("$2a$12$abcdefghijklmnopqrstuuSomeBcryptHashValue123");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Duration LIFETIME = Duration.ofDays(14);

    @Autowired JpaUserRepository users;
    @Autowired JpaSessionRepository sessions;
    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aUserRoundTripsWithRolesLanguagesAndPasswordHash() {
        User aysel = User.registerLearner(Email.of("Aysel@Example.com"), HASH, "Aysel", AZ, AZ);
        users.save(aysel);
        em.flush();
        em.clear();

        User loaded = users.findById(aysel.id()).orElseThrow();
        assertThat(loaded.email().value()).isEqualTo("aysel@example.com");
        assertThat(loaded.passwordHash()).isEqualTo(HASH);
        assertThat(loaded.displayName()).contains("Aysel");
        assertThat(loaded.roles()).containsExactly(Role.LEARNER);
        assertThat(loaded.supportLanguage()).isEqualTo(AZ);
        assertThat(loaded.targetLanguage()).isEqualTo(LanguageCode.ENGLISH);
        assertThat(loaded.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(users.findByEmail(Email.of("aysel@example.com"))).map(User::id).contains(aysel.id());

        // The hash lives in user_credential, keyed by the internal id; app_user has the public id.
        assertThat(jdbc.queryForObject("""
                select c.password_hash from user_credential c join app_user u on u.id = c.user_id where u.public_id = ?
                """, String.class, aysel.id().value())).isEqualTo(HASH.value());
    }

    @Test
    void findsByEmailAndRole() {
        assertThat(users.existsWithRole(Role.ADMIN)).isFalse();
        users.save(User.createAdmin(Email.of("murad@example.com"), HASH, "Murad", java.time.Instant.parse("2026-01-01T00:00:00Z")));
        em.flush();

        assertThat(users.existsByEmail(Email.of("MURAD@example.com"))).isTrue();
        assertThat(users.existsByEmail(Email.of("nobody@example.com"))).isFalse();
        assertThat(users.existsWithRole(Role.ADMIN)).isTrue();
        assertThat(users.findById(UserId.newId())).isEmpty();
    }

    @Test
    void changesToAUserAreSaved() {
        User leyla = User.restore(UserId.newId(), Email.of("leyla@example.com"), HASH, null, AZ, LanguageCode.of("tr"),
                LanguageCode.ENGLISH, EnumSet.of(Role.TRANSLATOR, Role.AUTHOR), UserStatus.ACTIVE);
        users.save(leyla);
        em.flush();
        em.clear();

        PasswordHash newHash = new PasswordHash("$2a$12$zzzzzzzzzzzzzzzzzzzzzzAnotherBcryptHashValue12");
        leyla.changePassword(newHash);
        leyla.disable();
        users.save(leyla);
        em.flush();
        em.clear();

        User loaded = users.findById(leyla.id()).orElseThrow();
        assertThat(loaded.passwordHash()).isEqualTo(newHash);
        // The credential's timestamp comes from the injected clock, not the system time.
        assertThat(jdbc.queryForObject("""
                select c.updated_at from user_credential c join app_user u on u.id = c.user_id where u.public_id = ?
                """, Timestamp.class, leyla.id().value()).toInstant()).isEqualTo(CLOCK_TIME);
        assertThat(loaded.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(loaded.canAuthenticate()).isFalse();
        assertThat(loaded.roles()).containsExactlyInAnyOrder(Role.TRANSLATOR, Role.AUTHOR);
        assertThat(loaded.displayName()).isEmpty();
    }

    @Test
    void aSessionRoundTripsAsOneAggregateAndRotationIsLinked() {
        User aysel = savedLearner();
        Session started = Session.start(aysel.id(), hash('a'), NOW, LIFETIME);
        sessions.save(started);
        em.flush();
        em.clear();

        Session loaded = sessions.findByTokenHash(hash('a')).orElseThrow();
        assertThat(loaded).isEqualTo(started);
        assertThat(loaded.userId()).isEqualTo(aysel.id());

        var rotated = (Session.Rotated) loaded.present(hash('a'), hash('b'), NOW.plusSeconds(60), LIFETIME);
        sessions.save(loaded);
        em.flush();
        em.clear();

        // Found by ANY of its tokens, always as the whole session, tokens in issue order.
        Session reloaded = sessions.findByTokenHash(hash('b')).orElseThrow();
        assertThat(sessions.findByTokenHash(hash('a'))).contains(reloaded);
        assertThat(reloaded.tokens()).extracting(RefreshToken::hash).containsExactly(hash('a'), hash('b'));
        assertThat(reloaded.token(hash('a')).orElseThrow().replacedBy()).contains(hash('b'));
        assertThat(reloaded.newestToken().isUsable(NOW.plusSeconds(61))).isTrue();
        assertThat(reloaded.newestToken().expiresAt()).isEqualTo(rotated.next().expiresAt());
        // Stored as rows of one family, linked by id.
        assertThat(jdbc.queryForObject("""
                select count(*) from refresh_token t join refresh_token n on n.id = t.replaced_by
                where t.token_hash = ? and n.token_hash = ? and t.family_id = n.family_id
                """, Integer.class, hash('a').value(), hash('b').value())).isEqualTo(1);
    }

    @Test
    void aGraceRotationIsStoredAsAChain() {
        User aysel = savedLearner();
        Session session = Session.start(aysel.id(), hash('a'), NOW, LIFETIME);
        session.present(hash('a'), hash('b'), NOW, LIFETIME);
        sessions.save(session);
        em.flush();
        em.clear();

        Session again = sessions.findByTokenHash(hash('a')).orElseThrow();
        var outcome = again.present(hash('a'), hash('c'), NOW.plusSeconds(5), LIFETIME); // the reply to a → b was lost
        sessions.save(again);
        em.flush();
        em.clear();

        assertThat(outcome).isInstanceOf(Session.Rotated.class);
        Session stored = sessions.findByTokenHash(hash('c')).orElseThrow();
        assertThat(stored.token(hash('b')).orElseThrow().replacedBy()).contains(hash('c'));
        assertThat(stored.token(hash('c')).orElseThrow().isUsable(NOW.plusSeconds(5))).isTrue();
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void reuseRevokesTheWholeStoredSession() {
        User aysel = savedLearner();
        Session session = Session.start(aysel.id(), hash('a'), NOW, LIFETIME);
        session.present(hash('a'), hash('b'), NOW, LIFETIME);
        sessions.save(session);
        em.flush();
        em.clear();

        Session copied = sessions.findByTokenHash(hash('a')).orElseThrow();
        // After the grace window: theft, not a lost reply.
        var outcome = copied.present(hash('a'), hash('c'), NOW.plus(Session.REUSE_GRACE).plusSeconds(1), LIFETIME);
        sessions.save(copied);
        em.flush();
        em.clear();

        assertThat(outcome).isEqualTo(new Session.Rejected(Session.Reason.REUSED));
        assertThat(sessions.findByTokenHash(hash('b')).orElseThrow().isRevoked()).isTrue();
        assertThat(sessions.findByTokenHash(hash('c'))).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null", Integer.class)).isZero();
    }

    @Test
    void anUnknownTokenHasNoSession() {
        assertThat(sessions.findByTokenHash(hash('f'))).isEmpty();
    }

    private User savedLearner() {
        User aysel = User.registerLearner(Email.of("aysel@example.com"), HASH, "Aysel", AZ, AZ);
        users.save(aysel);
        em.flush();
        return aysel;
    }

    private static TokenHash hash(char c) {
        return new TokenHash(String.valueOf(c).repeat(64));
    }
}
