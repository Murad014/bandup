package com.bandup.web.layout;

import java.util.List;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the global navigation list to every view.
 *
 * <p>Making this a {@link ControllerAdvice} with a global {@link ModelAttribute}
 * means {@code navItems} is available to the shared layout on every page without
 * each controller having to add it. Individual controllers only set
 * {@code activeKey} to mark the current feature.
 */
@ControllerAdvice
public class NavigationAdvice {

    /** Keys are referenced by controllers via {@code activeKey}. */
    public static final String READING = "reading";
    public static final String GRAMMAR = "grammar";

    @ModelAttribute("navItems")
    public List<NavItem> navItems() {
        return List.of(
                // Reading is live.
                new NavItem(READING, "Reading", "/reading/yellowstone-wolves", true, false),
                // Grammar is planned — shown as a disabled "coming soon" entry.
                new NavItem(GRAMMAR, "Grammar", "#", false, true)
        );
    }
}
