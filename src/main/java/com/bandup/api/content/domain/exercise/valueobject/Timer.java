package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Optional;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * The countdown: a duration and an optional low-time warning (seconds left
 * when the timer turns red). An exercise without a timer has none of this.
 */
@ValueObject
public record Timer(int durationSeconds, Integer lowTimeThresholdSeconds) {

    public Timer {
        if (durationSeconds < 1) {
            throw DomainException.of("durationSeconds", "Duration must be greater than 0.");
        }
        if (lowTimeThresholdSeconds != null
                && (lowTimeThresholdSeconds < 1 || lowTimeThresholdSeconds > durationSeconds)) {
            throw DomainException.of("lowTimeThresholdSeconds", "The low-time warning must be between 1 second and the duration.");
        }
    }

    public Optional<Integer> lowTimeThreshold() {
        return Optional.ofNullable(lowTimeThresholdSeconds);
    }
}
