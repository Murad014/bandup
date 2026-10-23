package com.bandup.api.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.domain.session.aggregate.Session;
import com.bandup.api.identity.domain.session.entity.RefreshToken;
import com.bandup.api.identity.domain.session.repository.SessionRepository;
import com.bandup.api.identity.domain.session.valueobject.SessionId;
import com.bandup.api.identity.domain.session.valueobject.TokenHash;
import com.bandup.api.identity.domain.user.valueobject.UserId;

/**
 * {@link SessionRepository} on JPA. A session is the set of {@code refresh_token}
 * rows sharing a {@code family_id}; it's loaded (and locked) and saved as one
 * aggregate. The domain links tokens by hash, the table by id
 * ({@code replaced_by}), so new tokens are saved first and linked afterwards.
 *
 * <p>Entities use field access, and Hibernate may return lazy proxies whose
 * fields are empty (reads see null, writes are lost), so every entity is
 * unproxied before its fields are read or written.
 */
@Repository
class JpaSessionRepository implements SessionRepository {

    private final RefreshTokenJpaRepository tokens;
    private final UserJpaRepository users;
    private final EntityManager entityManager;

    JpaSessionRepository(RefreshTokenJpaRepository tokens, UserJpaRepository users, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    public Optional<Session> findByTokenHash(TokenHash hash) {
        return tokens.findFamilyIdByTokenHash(hash.value()).flatMap(familyId -> {
            // Lock twice. The first query waits until no one else is changing the session. In
            // PostgreSQL's READ COMMITTED, a FOR UPDATE that had to wait returns the latest
            // version of the rows it locked but can't see rows inserted meanwhile (e.g. the token
            // a concurrent refresh just added). Every writer locks the existing rows first, so
            // once we hold them no one can add more: the second query sees, and locks, the
            // complete current session.
            tokens.lockFamily(familyId);
            List<RefreshTokenEntity> rows = tokens.lockFamily(familyId).stream().map(JpaSessionRepository::real).toList();
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            UserEntity user = (UserEntity) Hibernate.unproxy(rows.get(0).user);
            List<RefreshToken> domainTokens = rows.stream().map(JpaSessionRepository::toDomain).toList();
            return Optional.of(Session.restore(new SessionId(familyId), new UserId(user.publicId), domainTokens));
        });
    }

    @Override
    @Transactional
    public void save(Session session) {
        UUID familyId = session.id().value();
        UserEntity user = users.findByPublicId(session.userId().value())
                .map(u -> (UserEntity) Hibernate.unproxy(u))
                .orElseThrow(() -> new IllegalStateException("No user " + session.userId() + " for session " + familyId));
        Map<String, RefreshTokenEntity> byHash = new HashMap<>();
        tokens.findByFamilyId(familyId).stream().map(JpaSessionRepository::real).forEach(e -> byHash.put(e.tokenHash, e));

        // 1. Insert or update every token without its replaced_by link.
        for (RefreshToken token : session.tokens()) {
            RefreshTokenEntity entity = byHash.computeIfAbsent(token.hash().value(), h -> new RefreshTokenEntity());
            entity.user = user;
            entity.familyId = familyId;
            entity.tokenHash = token.hash().value();
            entity.issuedAt = token.issuedAt();
            entity.expiresAt = token.expiresAt();
            entity.revokedAt = token.revokedAt().orElse(null);
            byHash.put(entity.tokenHash, tokens.save(entity));
        }
        tokens.flush();

        // 2. Link replaced tokens to their successors (which now have ids).
        for (RefreshToken token : session.tokens()) {
            token.replacedBy().ifPresent(next -> byHash.get(token.hash().value()).replacedBy = byHash.get(next.value()));
        }
    }

    private static RefreshToken toDomain(RefreshTokenEntity e) {
        RefreshTokenEntity successor = real(e.replacedBy);
        return RefreshToken.restore(
                new TokenHash(e.tokenHash),
                e.issuedAt,
                e.expiresAt,
                successor == null ? null : new TokenHash(successor.tokenHash),
                e.revokedAt);
    }

    /** The real entity behind a possible lazy proxy (null stays null). */
    private static RefreshTokenEntity real(RefreshTokenEntity entity) {
        return (RefreshTokenEntity) Hibernate.unproxy(entity);
    }

    @Override
    @Transactional
    public int deleteEndedBefore(Instant cutoff) {
        // One statement per call: replaced_by points inside the same session, and the whole session goes at once.
        return entityManager.createNativeQuery("""
                delete from refresh_token where family_id in (
                    select family_id from refresh_token group by family_id
                    -- coalesce: a never-revoked token gives NULL here, and bool_and would skip it (deleting live sessions)
                    having bool_and(replaced_by is not null or coalesce(revoked_at < ?1, false) or expires_at < ?1))""")
                .setParameter(1, Timestamp.from(cutoff)).executeUpdate();
    }
}
