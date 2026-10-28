package com.bandup.api.identity.domain.user.aggregate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.InstructorStatus;
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
 *
 * <p>Signing in needs an active account with a confirmed email (requirement
 * 12.2). An Instructor sign-up is a request an admin approves or rejects
 * (12.1); approval adds the AUTHOR role.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class User {

    private static final int MAX_DISPLAY_NAME = 80;

    @EqualsAndHashCode.Include
    @Getter
    private final UserId id;
    @Getter
    private final Email email;
    @Getter
    private PasswordHash passwordHash;
    private final String displayName;
    @Getter
    private final LanguageCode uiLanguage;
    @Getter
    private final LanguageCode supportLanguage;
    @Getter
    private final LanguageCode targetLanguage;
    private final Set<Role> roles;
    @Getter
    private UserStatus status;
    private Instant emailVerifiedAt;
    @Getter
    private InstructorStatus instructorStatus;
    private Instant instructorRequestedAt;

    private User(UserId id, Email email, PasswordHash passwordHash, String displayName, LanguageCode uiLanguage,
            LanguageCode supportLanguage, LanguageCode targetLanguage, Set<Role> roles, UserStatus status,
            Instant emailVerifiedAt, InstructorStatus instructorStatus, Instant instructorRequestedAt) {
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
        this.emailVerifiedAt = emailVerifiedAt;
        this.instructorStatus = Objects.requireNonNull(instructorStatus, "instructorStatus");
        this.instructorRequestedAt = instructorRequestedAt;
        if (instructorStatus != InstructorStatus.NONE && instructorRequestedAt == null) {
            throw new IllegalArgumentException("An instructor request needs its time");
        }
    }

    /**
     * Someone who signed up: active, LEARNER role, learning English, email not
     * confirmed yet. With {@code wantsInstructor} the account also carries a
     * pending Instructor request.
     */
    public static User register(Email email, PasswordHash passwordHash, String displayName,
            LanguageCode uiLanguage, LanguageCode supportLanguage, boolean wantsInstructor, Instant now) {
        return new User(UserId.newId(), email, passwordHash, displayName, uiLanguage, supportLanguage,
                LanguageCode.ENGLISH, EnumSet.of(Role.LEARNER), UserStatus.ACTIVE, null,
                wantsInstructor ? InstructorStatus.PENDING : InstructorStatus.NONE, wantsInstructor ? now : null);
    }

    /** A learner (no Instructor request) whose email is not confirmed yet. */
    public static User registerLearner(Email email, PasswordHash passwordHash, String displayName,
            LanguageCode uiLanguage, LanguageCode supportLanguage) {
        return register(email, passwordHash, displayName, uiLanguage, supportLanguage, false, null);
    }

    /** The first administrator, created from configuration at startup; its email counts as confirmed. */
    public static User createAdmin(Email email, PasswordHash passwordHash, String displayName, Instant now) {
        return new User(UserId.newId(), email, passwordHash, displayName, LanguageCode.ENGLISH,
                LanguageCode.ENGLISH, LanguageCode.ENGLISH, EnumSet.of(Role.ADMIN), UserStatus.ACTIVE,
                Objects.requireNonNull(now, "now"), InstructorStatus.NONE, null);
    }

    /** Rebuilds a stored user (for the persistence adapter). */
    public static User restore(UserId id, Email email, PasswordHash passwordHash, String displayName,
            LanguageCode uiLanguage, LanguageCode supportLanguage, LanguageCode targetLanguage, Set<Role> roles,
            UserStatus status, Instant emailVerifiedAt, InstructorStatus instructorStatus, Instant instructorRequestedAt) {
        return new User(id, email, passwordHash, displayName, uiLanguage, supportLanguage, targetLanguage, roles,
                status, emailVerifiedAt, instructorStatus, instructorRequestedAt);
    }

    /** Rebuilds a confirmed user without an Instructor request. */
    public static User restore(UserId id, Email email, PasswordHash passwordHash, String displayName,
            LanguageCode uiLanguage, LanguageCode supportLanguage, LanguageCode targetLanguage, Set<Role> roles,
            UserStatus status) {
        return restore(id, email, passwordHash, displayName, uiLanguage, supportLanguage, targetLanguage, roles,
                status, Instant.EPOCH, InstructorStatus.NONE, null);
    }

    /** Only active users with a confirmed email may sign in or refresh their session. */
    public boolean canAuthenticate() {
        return status.canAuthenticate() && isEmailVerified();
    }

    /** Active, but the email isn't confirmed yet (sign-in says so, after a correct password). */
    public boolean awaitsEmailConfirmation() {
        return status.canAuthenticate() && !isEmailVerified();
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    /** Confirms the email (link opened, or a password reset proved the inbox). Confirming again changes nothing. */
    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Objects.requireNonNull(now, "now");
        }
    }

    public Optional<Instant> instructorRequestedAt() {
        return Optional.ofNullable(instructorRequestedAt);
    }

    /**
     * An admin approves the Instructor request: the user becomes an AUTHOR.
     *
     * @throws IllegalStateException unless the request is pending
     */
    public void approveInstructor() {
        requirePendingRequest();
        instructorStatus = InstructorStatus.APPROVED;
        roles.add(Role.AUTHOR);
    }

    /**
     * An admin turns the Instructor request down; the account stays a learner.
     *
     * @throws IllegalStateException unless the request is pending
     */
    public void rejectInstructor() {
        requirePendingRequest();
        instructorStatus = InstructorStatus.REJECTED;
    }

    private void requirePendingRequest() {
        if (instructorStatus != InstructorStatus.PENDING) {
            throw new IllegalStateException("No pending Instructor request (status " + instructorStatus + ")");
        }
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

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }

    /** Aggregates are equal by identity. */

    /**
     * The display name as it will be stored: trimmed, blank = none.
     *
     * @throws DomainException ({@code displayName}) if it's longer than 80 characters
     */
    public static String displayName(String raw) {
        return normaliseDisplayName(raw);
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
