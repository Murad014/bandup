package com.bandup.api.shared.domain.numbering;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * The question numbers one question takes: a single number ("7") or, for a
 * "Choose N" question, N numbers in a row ("7–8").
 */
@ValueObject
public record NumberRange(int first, int last) {

    public NumberRange {
        if (first < 1 || last < first) {
            throw new IllegalArgumentException("Invalid question numbers " + first + "–" + last);
        }
    }

    public int size() {
        return last - first + 1;
    }

    public boolean contains(int number) {
        return number >= first && number <= last;
    }

    @Override
    public String toString() {
        return first == last ? Integer.toString(first) : first + "–" + last;
    }
}
