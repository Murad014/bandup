package com.bandup.api.vocabulary.application;

import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.vocabulary.domain.session.repository.WordSessionRepository;

/** Deletes sessions older than 7 days (design §7). */
@Service
@RequiredArgsConstructor
public class SessionCleanup {

    static final Duration KEEP = Duration.ofDays(7);

    private final WordSessionRepository sessions;
    private final Clock clock;

    @Transactional
    public int purge() {
        return sessions.deleteStartedBefore(clock.instant().minus(KEEP));
    }
}
