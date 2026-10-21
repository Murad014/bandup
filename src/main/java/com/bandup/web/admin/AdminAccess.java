package com.bandup.web.admin;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stand-in admin-role check until a real authentication spec is implemented.
 *
 * <p>Deny-by-default: a request is treated as admin ONLY if it carries the
 * {@link #SESSION_ROLE} session attribute, or if the dev toggle
 * {@code app.admin.dev-allow} is enabled (local development only). When real
 * authentication arrives, only this class changes; the guard
 * ({@link AdminAccessFilter}) and the navbar gating stay the same, so the admin
 * area is never accidentally exposed.
 */
@Component
public class AdminAccess {

    /** Session attribute that marks the current session as an admin. */
    public static final String SESSION_ROLE = "ADMIN";

    private final boolean devAllow;

    public AdminAccess(@Value("${app.admin.dev-allow:false}") boolean devAllow) {
        this.devAllow = devAllow;
    }

    public boolean isAdmin(HttpServletRequest request) {
        if (devAllow) {
            return true;
        }
        var session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ROLE));
    }
}
