package com.bandup.api.identity.application.port;

import java.time.Duration;

/** Port: authentication settings from configuration. */
public interface AuthSettings {

    /** How long a refresh token (and so a login session without activity) lasts. */
    Duration refreshTokenLifetime();

    /** The site's address for links in emails, e.g. {@code https://bandup.az} (no trailing slash). */
    String publicUrl();
}
