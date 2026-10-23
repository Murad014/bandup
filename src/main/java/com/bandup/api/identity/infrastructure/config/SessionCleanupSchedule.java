package com.bandup.api.identity.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.PurgeEndedSessions;

/** Runs the session clean-up every night (03:30 server time; {@code bandup.auth.cleanup-cron} to change it). */
@Component
class SessionCleanupSchedule {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupSchedule.class);

    private final PurgeEndedSessions purge;

    SessionCleanupSchedule(PurgeEndedSessions purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "${bandup.auth.cleanup-cron:0 30 3 * * *}")
    void nightly() {
        int[] deleted = purge.purge();
        log.info("Session clean-up: {} ended refresh tokens and {} idle sign-in counters deleted", deleted[0], deleted[1]);
    }
}
