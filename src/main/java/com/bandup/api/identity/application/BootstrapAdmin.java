package com.bandup.api.identity.application;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.application.port.PasswordHasher;
import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.Email;
import com.bandup.api.identity.domain.user.valueobject.NewPassword;
import com.bandup.api.identity.domain.user.valueobject.Role;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Use case: create the first administrator from configuration (requirement
 * 8.9). Does nothing if an admin already exists. Never turns an existing
 * account into an admin. Not one transaction, so BCrypt never holds a
 * database connection; the single insert runs in its own transaction.
 */
@Service
@RequiredArgsConstructor
public class BootstrapAdmin {

    public enum Result {
        CREATED,
        ADMIN_ALREADY_EXISTS,
        EMAIL_TAKEN_BY_NON_ADMIN
    }

    private final UserRepository users;
    private final PasswordHasher hasher;

    /** @throws com.bandup.api.shared.domain.error.DomainException if the configured email or password is invalid */
    public Result ensureAdmin(String rawEmail, String rawPassword) {
        return ensureAdmin(rawEmail, rawPassword, false);
    }

    /**
     * @param localDevelopment the {@code local} profile: a short password (e.g. "admin") is allowed
     * @throws com.bandup.api.shared.domain.error.DomainException if the configured email or password is invalid
     */
    public Result ensureAdmin(String rawEmail, String rawPassword, boolean localDevelopment) {
        if (users.existsWithRole(Role.ADMIN)) {
            return Result.ADMIN_ALREADY_EXISTS;
        }
        Email email = Email.of(rawEmail);
        NewPassword password = localDevelopment ? NewPassword.forLocalDevelopment(rawPassword) : NewPassword.of(rawPassword);
        if (users.existsByEmail(email)) {
            return Result.EMAIL_TAKEN_BY_NON_ADMIN;
        }
        users.save(User.createAdmin(email, hasher.hash(password), "Administrator"));
        return Result.CREATED;
    }
}
