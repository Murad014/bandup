package com.bandup.api.identity.infrastructure.persistence;

import java.time.Clock;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.shared.domain.language.LanguageCode;

/**
 * {@link UserRepository} on JPA. Maps by the public id; the numeric id stays
 * here. Entities may be lazy proxies (fields empty), so they're unproxied
 * before any field is read or written.
 */
@Repository
class JpaUserRepository implements UserRepository {

    private final UserJpaRepository users;
    private final Clock clock;

    JpaUserRepository(UserJpaRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return users.findByPublicId(id.value()).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return users.findByEmail(email.value()).map(JpaUserRepository::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return users.existsByEmail(email.value());
    }

    @Override
    public boolean existsWithRole(Role role) {
        return users.existsWithRole(role);
    }

    @Override
    @Transactional
    public void save(User user) {
        UserEntity entity = users.findByPublicId(user.id().value()).map(e -> (UserEntity) Hibernate.unproxy(e)).orElseGet(() -> {
            UserEntity created = new UserEntity();
            created.publicId = user.id().value();
            return created;
        });
        entity.email = user.email().value();
        entity.displayName = user.displayName().orElse(null);
        entity.uiLanguage = user.uiLanguage().value();
        entity.supportLanguage = user.supportLanguage().value();
        entity.targetLanguage = user.targetLanguage().value();
        entity.status = user.status();
        entity.roles.clear();
        entity.roles.addAll(user.roles());
        if (!user.passwordHash().value().equals(entity.passwordHash)) {
            entity.passwordHash = user.passwordHash().value();
            entity.credentialUpdatedAt = clock.instant();
        }
        users.save(entity);
    }

    /**
     * The entity may be a lazy proxy (e.g. loaded as a refresh token's user in
     * the same transaction); its fields are only filled after unproxying.
     */
    static User toDomain(UserEntity entity) {
        UserEntity e = (UserEntity) Hibernate.unproxy(entity);
        return User.restore(
                new UserId(e.publicId),
                Email.of(e.email),
                new PasswordHash(e.passwordHash),
                e.displayName,
                LanguageCode.of(e.uiLanguage),
                LanguageCode.of(e.supportLanguage),
                LanguageCode.of(e.targetLanguage),
                e.roles,
                e.status);
    }
}
