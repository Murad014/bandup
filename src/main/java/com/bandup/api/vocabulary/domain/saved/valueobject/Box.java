package com.bandup.api.vocabulary.domain.saved.valueobject;

import java.time.LocalDate;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A saved word's review box (vocabulary requirement 6.2): 1 to 4. Next due:
 * box 1 tomorrow, 2 in 3 days, 3 in a week, 4 in two weeks.
 */
@ValueObject
public record Box(int value) {

    private static final int[] DAYS = {1, 3, 7, 14};

    public Box {
        if (value < 1 || value > 4) {
            throw new IllegalArgumentException("A box is 1 to 4: " + value);
        }
    }

    public static Box first() {
        return new Box(1);
    }

    public boolean isLast() {
        return value == 4;
    }

    public Box up() {
        return new Box(Math.min(value + 1, 4));
    }

    public LocalDate dueAfter(LocalDate day) {
        return day.plusDays(DAYS[value - 1]);
    }
}
