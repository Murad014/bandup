package com.bandup.api.identity.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    @Query("select t.familyId from RefreshTokenEntity t where t.tokenHash = :hash")
    Optional<UUID> findFamilyIdByTokenHash(String hash);

    /**
     * {@code SELECT ... FOR UPDATE} on every token of one session, in issue
     * order. No fetch joins: PostgreSQL doesn't allow FOR UPDATE on the
     * nullable side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t where t.familyId = :familyId order by t.issuedAt, t.id")
    List<RefreshTokenEntity> lockFamily(UUID familyId);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);
}
