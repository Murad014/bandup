package com.bandup.api.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** {@code refresh_token}: one issued refresh token (hash only). */
@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    UserEntity user;

    @Column(name = "token_hash", nullable = false, unique = true)
    String tokenHash;

    @Column(name = "family_id", nullable = false)
    UUID familyId;

    @Column(nullable = false)
    Instant issuedAt;

    @Column(nullable = false)
    Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by")
    RefreshTokenEntity replacedBy;

    Instant revokedAt;
}
