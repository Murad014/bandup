package com.bandup.ui;

import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every page the navbar items. Data only: who is signed in (and so
 * whether the Admin link shows) is decided in the browser from the access
 * token, never by the server.
 */
@ControllerAdvice(basePackages = "com.bandup.ui")
public class NavigationAdvice {

    public static final String HOME = "home";
    public static final String READING = "reading";
    public static final String GRAMMAR = "grammar";

    @ModelAttribute("navItems")
    public List<NavItem> navItems() {
        return List.of(
                new NavItem(READING, "Reading", "/reading/yellowstone-wolves", true, false),
                new NavItem(GRAMMAR, "Grammar", "#", false, true));
    }
}
