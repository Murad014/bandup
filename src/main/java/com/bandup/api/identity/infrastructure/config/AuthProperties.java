package com.bandup.api.identity.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.bandup.api.identity.application.port.AuthSettings;

/**
 * {@code bandup.auth.*}.
 *
 * @param refreshTokenLifetime how long a login session lasts without being refreshed
 * @param adminEmail           first admin's email ({@code BANDUP_ADMIN_EMAIL}); blank = don't create one
 * @param adminPassword        first admin's password ({@code BANDUP_ADMIN_PASSWORD}); never committed
 * @param allowShortAdminPassword only in the {@code local} profile: the first admin may have a short password
 */
@ConfigurationProperties("bandup.auth")
record AuthProperties(Duration refreshTokenLifetime, String adminEmail, String adminPassword,
        boolean allowShortAdminPassword) implements AuthSettings {

    AuthProperties {
        refreshTokenLifetime = refreshTokenLifetime == null ? Duration.ofDays(14) : refreshTokenLifetime;
    }

    @Override
    public String toString() {
        return "AuthProperties[refreshTokenLifetime=" + refreshTokenLifetime + ", adminEmail=" + adminEmail + "]";
    }
}
