package com.bandup.api.identity.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bandup.api.identity.application.PurgeEndedSessions;

/** Runs the session clean-up every night (03:30 server time; {@code bandup.auth.cleanup-cron} to change it). */
@Component
@Slf4j
@RequiredArgsConstructor
class SessionCleanupSchedule {

    private final PurgeEndedSessions purge;

    @Scheduled(cron = "${bandup.auth.cleanup-cron:0 30 3 * * *}")
    void nightly() {
        int[] deleted = purge.purge();
        log.info("Session clean-up: {} ended refresh tokens, {} idle sign-in counters and {} ended email links deleted",
                deleted[0], deleted[1], deleted[2]);
    }
}
