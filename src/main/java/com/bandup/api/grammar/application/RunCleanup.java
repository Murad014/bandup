package com.bandup.api.grammar.application;

import java.time.Clock;
import java.time.Duration;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.domain.run.repository.LessonRunRepository;

/** Deletes unfinished runs older than 7 days (grammar requirement 8.4). */
@Service
@RequiredArgsConstructor
public class RunCleanup {

    static final Duration KEEP = Duration.ofDays(7);

    private final LessonRunRepository runs;
    private final Clock clock;

    /** @return how many runs were deleted */
    @Transactional
    public int purge() {
        return runs.deleteUnfinishedStartedBefore(clock.instant().minus(KEEP));
    }
}
