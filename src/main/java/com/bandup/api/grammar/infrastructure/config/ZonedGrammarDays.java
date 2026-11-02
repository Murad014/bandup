package com.bandup.api.grammar.infrastructure.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.port.GrammarDays;

/** {@link GrammarDays} from the clock in {@code bandup.grammar.day-zone} (default Asia/Baku). */
@Component
class ZonedGrammarDays implements GrammarDays {

    private final Clock clock;
    private final ZoneId zone;

    ZonedGrammarDays(Clock clock, @Value("${bandup.grammar.day-zone:Asia/Baku}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Override
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
