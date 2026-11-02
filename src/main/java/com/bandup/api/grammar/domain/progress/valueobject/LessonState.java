package com.bandup.api.grammar.domain.progress.valueobject;

import java.util.Objects;
import java.util.Optional;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * What a learner may do with one course lesson (grammar requirements 2.2, 3):
 * replay it (DONE), start it (NEXT) or not yet (LOCKED, with the reason).
 *
 * @param reason only for LOCKED; null otherwise
 */
@ValueObject
public record LessonState(LessonId lesson, State state, LockReason reason) {

    public LessonState {
        Objects.requireNonNull(lesson, "lesson");
        Objects.requireNonNull(state, "state");
        if ((state == State.LOCKED) != (reason != null)) {
            throw new IllegalArgumentException("Only a locked lesson has a reason");
        }
    }

    public static LessonState done(LessonId lesson) {
        return new LessonState(lesson, State.DONE, null);
    }

    public static LessonState next(LessonId lesson) {
        return new LessonState(lesson, State.NEXT, null);
    }

    public static LessonState locked(LessonId lesson, LockReason reason) {
        return new LessonState(lesson, State.LOCKED, Objects.requireNonNull(reason, "reason"));
    }

    public boolean canOpen() {
        return state != State.LOCKED;
    }

    public Optional<LockReason> lockReason() {
        return Optional.ofNullable(reason);
    }

    public enum State {
        DONE,
        NEXT,
        LOCKED
    }

    public enum LockReason {
        /** The daily new-lesson limit is reached. */
        TOMORROW,
        /** An earlier lesson isn't finished yet. */
        FINISH_PREVIOUS,
        /** Guests may open only the first lesson. */
        SIGN_IN
    }
}
