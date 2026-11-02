package com.bandup.api.grammar.domain.run.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Where a run stands after one answer.
 *
 * @param mastered     check items answered correctly so far
 * @param total        check items of the lesson
 * @param justFinished this answer mastered the last check item
 */
@ValueObject
public record RunOutcome(int mastered, int total, int retries, boolean finished, boolean justFinished) {
}
