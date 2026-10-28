package com.bandup.api.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.bandup.api.identity.domain.user.valueobject.InstructorStatus;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

/** {@code app_user} + {@code user_role} + {@code user_credential} (as a secondary table). */
@Entity
@Table(name = "app_user")
@SecondaryTable(name = "user_credential", pkJoinColumns = @PrimaryKeyJoinColumn(name = "user_id"))
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    UUID publicId;

    String email;

    String displayName;

    @Column(nullable = false)
    String uiLanguage;

    @Column(nullable = false)
    String supportLanguage;

    @Column(nullable = false)
    String targetLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UserStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(table = "user_credential", name = "password_hash", nullable = false)
    String passwordHash;

    @Column(table = "user_credential", name = "updated_at", nullable = false)
    Instant credentialUpdatedAt;

    Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    InstructorStatus instructorStatus = InstructorStatus.NONE;

    Instant instructorRequestedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    Instant updatedAt;
}
