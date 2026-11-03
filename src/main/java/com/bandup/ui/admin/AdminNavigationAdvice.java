package com.bandup.ui.admin;

import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives the admin pages their sidebar items. */
@ControllerAdvice(basePackageClasses = AdminNavigationAdvice.class)
public class AdminNavigationAdvice {

    public static final String DASHBOARD = "dashboard";
    public static final String SESSIONS = "sessions";
    /** Grammar lessons (grammar requirement 12). */
    public static final String GRAMMAR = "grammar";
    /** Word cards (vocabulary requirement 8). */
    public static final String VOCABULARY = "vocabulary";
    /** ADMIN only: guard.js hides the link from authors. */
    public static final String INSTRUCTORS = "instructors";

    @ModelAttribute("adminNav")
    public List<AdminNavItem> adminNav() {
        return List.of(
                new AdminNavItem(DASHBOARD, "Dashboard", "/admin"),
                new AdminNavItem(SESSIONS, "Sessions", "/admin/sessions"),
                new AdminNavItem(GRAMMAR, "Grammar", "/admin/grammar"),
                new AdminNavItem(VOCABULARY, "Vocabulary", "/admin/vocabulary"),
                new AdminNavItem(INSTRUCTORS, "Instructors", "/admin/instructors"));
    }
}
