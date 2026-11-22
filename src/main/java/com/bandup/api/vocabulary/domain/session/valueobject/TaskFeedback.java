package com.bandup.api.vocabulary.domain.session.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;

/**
 * The server's answer to one task.
 *
 * @param word     the word the task was about (null for a match)
 * @param mastered master tasks right so far; {@code total} of them
 * @param finished every master task is mastered
 * @param firstTry the task wasn't answered wrong before in this session (requirement 6.6)
 */
@ValueObject
public record TaskFeedback(TaskId task, SavedWordId word, TaskPhase phase, boolean correct, String expected,
        String why, String explanation, int mastered, int total, boolean finished, boolean firstTry) {
}
