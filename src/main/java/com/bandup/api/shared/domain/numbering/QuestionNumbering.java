package com.bandup.api.shared.domain.numbering;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;

import com.bandup.api.shared.domain.ddd.DomainService;

/**
 * Numbers questions from their order and points (backend requirement 2.4): a
 * question worth N points takes N numbers, so a "Choose TWO" question after Q6
 * is Q7–8 and the next one is Q9.
 */
@DomainService
@UtilityClass
public final class QuestionNumbering {

    /**
     * @param pointsInOrder each question's points, in the order the learner sees them
     * @return each question's numbers, in the same order, starting at 1
     */
    public static List<NumberRange> number(List<Integer> pointsInOrder) {
        List<NumberRange> ranges = new ArrayList<>(pointsInOrder.size());
        int next = 1;
        for (int points : pointsInOrder) {
            if (points < 1) {
                throw new IllegalArgumentException("A question is worth at least 1 point, got " + points);
            }
            ranges.add(new NumberRange(next, next + points - 1));
            next += points;
        }
        return List.copyOf(ranges);
    }
}
