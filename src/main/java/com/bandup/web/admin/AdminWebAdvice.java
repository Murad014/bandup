package com.bandup.web.admin;

import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Supplies the admin sidebar items to admin views and an {@code isAdmin} flag
 * used by the public navbar to show/hide the role-gated Admin entry.
 */
@ControllerAdvice
public class AdminWebAdvice {

    public static final String DASHBOARD = "dashboard";
    public static final String SESSIONS = "sessions";

    private final AdminAccess adminAccess;

    public AdminWebAdvice(AdminAccess adminAccess) {
        this.adminAccess = adminAccess;
    }

    @ModelAttribute("adminNav")
    public List<AdminNavItem> adminNav() {
        return List.of(
                new AdminNavItem(DASHBOARD, "Dashboard", "/admin"),
                new AdminNavItem(SESSIONS, "Sessions", "/admin/sessions")
        );
    }

    /** Drives visibility of the public navbar's Admin entry point. */
    @ModelAttribute("isAdmin")
    public boolean isAdmin(HttpServletRequest request) {
        return adminAccess.isAdmin(request);
    }
}
