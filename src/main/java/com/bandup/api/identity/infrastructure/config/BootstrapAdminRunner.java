package com.bandup.api.identity.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.BootstrapAdmin;

/**
 * Creates the first admin at startup when {@code BANDUP_ADMIN_EMAIL} and
 * {@code BANDUP_ADMIN_PASSWORD} are set and no admin exists yet. An invalid
 * configured password stops startup with a clear message.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class BootstrapAdminRunner implements ApplicationRunner {

    private final BootstrapAdmin bootstrapAdmin;
    private final AuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.adminEmail() == null || properties.adminEmail().isBlank()) {
            return;
        }
        switch (bootstrapAdmin.ensureAdmin(properties.adminEmail(), properties.adminPassword(),
                properties.allowShortAdminPassword())) {
            case CREATED -> log.info("Created the first admin account: {}{}", properties.adminEmail(),
                    properties.allowShortAdminPassword() ? " (local development login)" : "");
            case ADMIN_ALREADY_EXISTS -> log.debug("An admin already exists; BANDUP_ADMIN_EMAIL ignored.");
            case EMAIL_TAKEN_BY_NON_ADMIN -> log.warn("BANDUP_ADMIN_EMAIL {} belongs to an existing non-admin account; "
                    + "it was NOT made an admin.", properties.adminEmail());
        }
    }
}
