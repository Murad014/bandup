package com.bandup.api.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bandup.api.identity.domain.user.valueobject.Role;

interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPublicId(UUID publicId);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from UserEntity u join u.roles r where r = :role")
    boolean existsWithRole(Role role);

    java.util.List<UserEntity> findByInstructorStatusOrderByInstructorRequestedAt(
            com.bandup.api.identity.domain.user.valueobject.InstructorStatus status);
}
