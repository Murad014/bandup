package com.bandup.api.grammar.domain.progress.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.LessonState;
import com.bandup.api.grammar.domain.progress.valueobject.LessonState.LockReason;
import com.bandup.api.shared.domain.ddd.DomainService;

/**
 * Which course lessons a learner may open (grammar requirement 3). The course
 * has no fixed length, so the caller passes only the lessons it shows (a
 * page) plus two facts about the whole course: its first lesson and the
 * learner's first unfinished one.
 *
 * <ul>
 *   <li>finished → DONE (always replayable)</li>
 *   <li>the first unfinished lesson → NEXT, unless today's new-lesson limit is
 *       reached → LOCKED "tomorrow"</li>
 *   <li>later lessons → LOCKED "finish the previous one"</li>
 *   <li>guests: the course's first lesson → NEXT, the rest → LOCKED "sign in"</li>
 * </ul>
 *
 * A lesson inserted before the learner's place simply becomes their NEXT one.
 */
@DomainService
public final class CourseUnlocking {

    /**
     * @param progress        the learner's progress; empty for a guest
     * @param firstUnfinished the learner's first unfinished lesson of the whole course (empty: all done)
     * @param firstOfCourse   the course's first lesson (empty: no lessons)
     */
    public LessonState stateOf(LessonId lesson, Optional<GrammarProgress> progress, CourseLevel level,
            Optional<LessonId> firstUnfinished, Optional<LessonId> firstOfCourse, LocalDate today) {
        Objects.requireNonNull(lesson, "lesson");
        if (progress.isEmpty()) {
            return firstOfCourse.filter(lesson::equals).isPresent() ? LessonState.next(lesson)
                    : LessonState.locked(lesson, LockReason.SIGN_IN);
        }
        GrammarProgress learner = progress.get();
        if (learner.hasFinished(lesson)) {
            return LessonState.done(lesson);
        }
        if (firstUnfinished.filter(lesson::equals).isPresent()) {
            return learner.mayStartNewLesson(level, today) ? LessonState.next(lesson)
                    : LessonState.locked(lesson, LockReason.TOMORROW);
        }
        return LessonState.locked(lesson, LockReason.FINISH_PREVIOUS);
    }

    /** {@link #stateOf} for each shown lesson, in the given order. */
    public List<LessonState> states(List<LessonId> shown, Optional<GrammarProgress> progress, CourseLevel level,
            Optional<LessonId> firstUnfinished, Optional<LessonId> firstOfCourse, LocalDate today) {
        return shown.stream()
                .map(lesson -> stateOf(lesson, progress, level, firstUnfinished, firstOfCourse, today))
                .toList();
    }
}
