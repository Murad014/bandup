package com.bandup.api.vocabulary.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * Writes again the cards left PREPARING for more than 2 minutes (the app
 * stopped while the AI wrote them, or the base form was busy).
 */
@Service
@RequiredArgsConstructor
public class CardRetryJob {

    static final Duration STALE = Duration.ofMinutes(2);

    private final WordCardRepository cards;
    private final CardWriter writer;
    private final Clock clock;

    public int retryStale() {
        List<WordId> stale = cards.findPreparingUpdatedBefore(clock.instant().minus(STALE));
        stale.forEach(writer::write);
        return stale.size();
    }
}
