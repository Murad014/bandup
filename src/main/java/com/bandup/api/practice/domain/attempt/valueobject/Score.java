package com.bandup.api.practice.domain.attempt.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** Points earned on one question, out of the points it is worth. */
@ValueObject
public record Score(int points, int possible) {

    public Score {
        if (possible < 1 || points < 0 || points > possible) {
            throw new IllegalArgumentException("Invalid score " + points + "/" + possible);
        }
    }

    public static Score zero(int possible) {
        return new Score(0, possible);
    }

    public boolean isCorrect() {
        return points == possible;
    }
}
