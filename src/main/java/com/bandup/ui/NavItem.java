package com.bandup.ui;

/**
 * One entry of the global navbar; the navbar is rendered from a list of these
 * ({@link NavigationAdvice}), so adding a feature is a data change.
 *
 * @param key        compared with the page's {@code activeKey}
 * @param href       link target; ignored when {@code enabled} is false
 * @param comingSoon shows a "soon" badge on a planned feature
 */
public record NavItem(String key, String label, String href, boolean enabled, boolean comingSoon) {
}
