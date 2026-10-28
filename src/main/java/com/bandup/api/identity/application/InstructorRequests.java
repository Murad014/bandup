package com.bandup.api.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.identity.domain.user.aggregate.User;
import com.bandup.api.identity.domain.user.repository.UserRepository;
import com.bandup.api.identity.domain.user.valueobject.InstructorStatus;
import com.bandup.api.identity.domain.user.valueobject.UserId;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.CurrentUser;
import com.bandup.api.shared.application.ForbiddenException;
import com.bandup.api.shared.application.NotFoundException;

/** Use case: admins approve or reject Instructor sign-ups (requirement 12.5). ADMIN only, not AUTHOR. */
@Service
@RequiredArgsConstructor
public class InstructorRequests {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final AccountEmails emails;

    /** A pending request as the admin sees it (the email is shown here: the admin needs to know who it is). */
    public record Request(UUID userId, String displayName, String email, boolean emailConfirmed, Instant requestedAt) {
    }

    public List<Request> pending() {
        requireAdmin();
        return users.findPendingInstructors().stream()
                .map(u -> new Request(u.id().value(), u.displayName().orElse(null), u.email().value(),
                        u.isEmailVerified(), u.instructorRequestedAt().orElse(null)))
                .toList();
    }

    /** Gives the AUTHOR role and emails the instructor. */
    public void approve(UUID userId) {
        User user = pendingUser(userId);
        user.approveInstructor();
        users.save(user);
        emails.sendInstructorApproved(user);
    }

    public void reject(UUID userId) {
        User user = pendingUser(userId);
        user.rejectInstructor();
        users.save(user);
    }

    private User pendingUser(UUID userId) {
        requireAdmin();
        User user = users.findById(new UserId(userId)).orElseThrow(() -> new NotFoundException("User not found."));
        if (user.instructorStatus() != InstructorStatus.PENDING) {
            throw new ConflictException("This request was already handled.");
        }
        return user;
    }

    private void requireAdmin() {
        if (currentUser.get().filter(caller -> caller.hasRole("ADMIN")).isEmpty()) {
            throw new ForbiddenException("Only an administrator can handle Instructor requests.");
        }
    }
}
