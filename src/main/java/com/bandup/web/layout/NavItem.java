package com.bandup.web.layout;

/**
 * A single entry in the global navigation bar.
 *
 * <p>The navbar is driven entirely by a list of these items (see
 * {@link NavigationAdvice}), so adding a new feature (e.g. Grammar) is a data
 * change — no feature page needs to be touched.
 *
 * @param key        stable identifier, compared against the page's activeKey
 * @param label      human-readable label shown in the navbar
 * @param href       link target; ignored when {@code enabled} is false
 * @param enabled    whether the item is a working link
 * @param comingSoon whether to show a "soon" badge for a planned feature
 */
public record NavItem(
        String key,
        String label,
        String href,
        boolean enabled,
        boolean comingSoon
) {
}
