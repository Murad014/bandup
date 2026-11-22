package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A word's progress after the first answer of a round (vocabulary requirement 6.6).
 *
 * @param streak  rounds in a row with a right first try, after the answer
 * @param changed false when the word was mastered already
 */
@ValueObject
public record SetStep(int streak, WordState state, boolean changed) {

    public SetStep {
        Objects.requireNonNull(state, "state");
    }

    public boolean mastered() {
        return state == WordState.MASTERED;
    }
}
