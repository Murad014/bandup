package com.bandup.api.vocabulary.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.CardRetryJob;
import com.bandup.api.vocabulary.application.SessionCleanup;

/** Every 2 minutes: cards left PREPARING are written again. Nightly: old sessions are deleted. */
@Component
@Slf4j
@RequiredArgsConstructor
class CardRetrySchedule {

    private final CardRetryJob retries;
    private final SessionCleanup cleanup;

    @Scheduled(fixedDelayString = "${bandup.vocabulary.retry-every:2m}", initialDelayString = "${bandup.vocabulary.retry-every:2m}")
    void stalePreparingCards() {
        int count = retries.retryStale();
        if (count > 0) {
            log.info("Vocabulary: wrote {} stale word cards again", count);
        }
    }

    @Scheduled(cron = "${bandup.vocabulary.cleanup-cron:0 50 3 * * *}")
    void nightly() {
        log.info("Vocabulary clean-up: {} old sessions deleted", cleanup.purge());
    }
}
