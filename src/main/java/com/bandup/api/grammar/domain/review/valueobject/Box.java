package com.bandup.api.grammar.domain.review.valueobject;

import java.time.LocalDate;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A card's box in the spaced review (grammar requirement 6.4): 1 to 4. A
 * correct answer moves up one box (4 stays 4), a wrong one back to box 1.
 * Next due: box 1 tomorrow, 2 in 3 days, 3 in a week, 4 in two weeks.
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

    public Box up() {
        return new Box(Math.min(value + 1, 4));
    }

    public LocalDate dueAfter(LocalDate day) {
        return day.plusDays(DAYS[value - 1]);
    }
}
