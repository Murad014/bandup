package com.bandup.web.admin;

/**
 * A single entry in the admin sidebar. The sidebar is rendered from a list of
 * these (see {@link AdminWebAdvice}), so adding an admin section is a data
 * change, not a per-page edit.
 */
public record AdminNavItem(String key, String label, String href) {
}
