package com.bandup.ui.admin;

import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives the admin pages their sidebar items. */
@ControllerAdvice(basePackageClasses = AdminNavigationAdvice.class)
public class AdminNavigationAdvice {

    public static final String DASHBOARD = "dashboard";
    public static final String SESSIONS = "sessions";

    @ModelAttribute("adminNav")
    public List<AdminNavItem> adminNav() {
        return List.of(
                new AdminNavItem(DASHBOARD, "Dashboard", "/admin"),
                new AdminNavItem(SESSIONS, "Sessions", "/admin/sessions"));
    }
}
