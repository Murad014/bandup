package com.bandup.api.vocabulary.domain.session.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * One graded answer.
 *
 * @param expected the right answer to show
 * @param why      for a wrong option, why it is wrong; null otherwise
 */
@ValueObject
public record Grade(boolean correct, String expected, String why) {
}
