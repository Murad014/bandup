package com.bandup.api.vocabulary.infrastructure.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.VocabularyDays;

/** {@link VocabularyDays} from the clock in {@code bandup.vocabulary.day-zone} (default Asia/Baku). */
@Component
class ZonedVocabularyDays implements VocabularyDays {

    private final Clock clock;
    private final ZoneId zone;

    ZonedVocabularyDays(Clock clock, @Value("${bandup.vocabulary.day-zone:Asia/Baku}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Override
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
