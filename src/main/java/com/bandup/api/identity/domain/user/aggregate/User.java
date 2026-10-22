package com.bandup.api.identity.domain.user.aggregate;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.PasswordHash;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.identity.domain.user.valueobject.UserStatus;
import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.language.LanguageCode;

/**
 * A person with an account (aggregate root). Holds who they are, how they sign
 * in (a password hash, never the password), what they may do (roles), and
 * which languages they use.
 */
@AggregateRoot
public final class User {

    private static final int MAX_DISPLAY_NAME = 80;

    private final UserId id;
    private final Email email;
    private PasswordHash passwordHash;
    private final String displayName;
    private final LanguageCode uiLanguage;
    private final LanguageCode supportLanguage;
    private final LanguageCode targetLanguage;
    private final Set<Role> roles;
    private UserStatus status;

    private User(UserId id, Email email, PasswordHash passwordHash, String displayName, LanguageCode uiLanguage,
            LanguageCode supportLanguage, LanguageCode targetLanguage, Set<Role> roles, UserStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = normaliseDisplayName(displayName);
        this.uiLanguage = Objects.requireNonNull(uiLanguage, "uiLanguage");
        this.supportLanguage = Objects.requireNonNull(supportLanguage, "supportLanguage");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("A user needs at least one role");
        }
        this.roles = EnumSet.copyOf(roles);
        this.status = Objects.requireNonNull(status, "status");
    }

    /** A new learner who signed up: active, LEARNER role, learning English. */
    public static User registerLearner(Email email, PasswordHash passwordHash, String displayName,
            LanguageCode uiLanguage, LanguageCode supportLanguage) {
        return new User(UserId.newId(), email, passwordHash, displayName, uiLanguage, supportLanguage,
                LanguageCode.ENGLISH, EnumSet.of(Role.LEARNER), UserStatus.ACTIVE);
    }

    /** The first administrator, created from configuration at startup. */
    public static User createAdmin(Email email, PasswordHash passwordHash, String displayName) {
        return new User(UserId.newId(), email, passwordHash, displayName, LanguageCode.ENGLISH,
                LanguageCode.ENGLISH, LanguageCode.ENGLISH, EnumSet.of(Role.ADMIN), UserStatus.ACTIVE);
    }

    /** Rebuilds a stored user (for the persistence adapter). */
    public static User restore(UserId id, Email email, PasswordHash passwordHash, String displayName,
            LanguageCode uiLanguage, LanguageCode supportLanguage, LanguageCode targetLanguage, Set<Role> roles,
            UserStatus status) {
        return new User(id, email, passwordHash, displayName, uiLanguage, supportLanguage, targetLanguage, roles,
                status);
    }

    /** Only active users may sign in or refresh their session. */
    public boolean canAuthenticate() {
        return status.canAuthenticate();
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** True for anyone allowed to use the admin API. */
    public boolean canAuthor() {
        return hasRole(Role.ADMIN) || hasRole(Role.AUTHOR);
    }

    public void changePassword(PasswordHash newHash) {
        this.passwordHash = Objects.requireNonNull(newHash, "newHash");
    }

    public void disable() {
        status = UserStatus.DISABLED;
    }

    public void markDeleted() {
        status = UserStatus.DELETED;
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public LanguageCode uiLanguage() {
        return uiLanguage;
    }

    public LanguageCode supportLanguage() {
        return supportLanguage;
    }

    public LanguageCode targetLanguage() {
        return targetLanguage;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    public UserStatus status() {
        return status;
    }

    /** Aggregates are equal by identity. */
    @Override
    public boolean equals(Object other) {
        return other instanceof User user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String normaliseDisplayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_DISPLAY_NAME) {
            throw DomainException.of("displayName", "Display name is too long (at most " + MAX_DISPLAY_NAME + " characters).");
        }
        return trimmed;
    }
}
