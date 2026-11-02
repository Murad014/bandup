package com.bandup.api.grammar.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.RunCleanup;

/** Deletes old unfinished lesson runs every night (03:45 server time; {@code bandup.grammar.cleanup-cron}). */
@Component
@Slf4j
@RequiredArgsConstructor
class RunCleanupSchedule {

    private final RunCleanup cleanup;

    @Scheduled(cron = "${bandup.grammar.cleanup-cron:0 45 3 * * *}")
    void nightly() {
        log.info("Grammar clean-up: {} unfinished lesson runs deleted", cleanup.purge());
    }
}
