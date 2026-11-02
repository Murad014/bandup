package com.bandup.api.grammar.application;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.port.GrammarDays;
import com.bandup.api.grammar.application.port.LearnerCourses;
import com.bandup.api.grammar.application.port.LearnerCourses.CourseFacts;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.repository.GrammarProgressRepository;
import com.bandup.api.grammar.domain.progress.service.CourseUnlocking;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.progress.valueobject.LessonState;
import com.bandup.api.shared.application.ForbiddenException;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.application.UnauthenticatedException;
import com.bandup.api.shared.domain.error.DomainException;

/**
 * Opens a published lesson for the caller, or refuses it (grammar requirement
 * 3.5): 404 if it isn't in a course, 401 for a guest beyond lesson 1, 403 when
 * it is locked for the learner.
 */
@Component
@RequiredArgsConstructor
class LessonAccess {

    private static final CourseUnlocking UNLOCKING = new CourseUnlocking();

    private final GrammarLessonRepository lessons;
    private final GrammarProgressRepository progresses;
    private final LearnerCourses courses;
    private final GrammarDays days;

    /** A lesson the caller may open, with its state (NEXT or DONE). */
    record Opened(GrammarLesson lesson, LessonState state) {
    }

    Opened open(String slug, Optional<LearnerId> learner) {
        GrammarLesson lesson = published(slug);
        Optional<GrammarProgress> progress = learner.map(this::progress);
        CourseFacts facts = courses.facts(lesson.level().name(), learner);
        LessonState state = UNLOCKING.stateOf(lesson.id(), progress, Learner.courseLevel(lesson.level()),
                facts.firstUnfinishedId(), facts.firstOfCourseId(), days.today());
        if (!state.canOpen()) {
            if (learner.isEmpty()) {
                throw new UnauthenticatedException("Sign in to continue the course.");
            }
            LessonState.LockReason reason = state.lockReason().orElseThrow();
            throw new ForbiddenException(switch (reason) {
                case TOMORROW -> "You've done today's new lesson. This one unlocks tomorrow.";
                case FINISH_PREVIOUS -> "Finish the lessons before this one first.";
                case SIGN_IN -> "Sign in to continue the course.";
            }, reason.name());
        }
        return new Opened(lesson, state);
    }

    /** The learner's progress; a learner who never started gets an empty one (not saved). */
    GrammarProgress progress(LearnerId learner) {
        return progresses.findByLearner(learner).orElseGet(() -> GrammarProgress.start(learner));
    }

    private GrammarLesson published(String slug) {
        try {
            return lessons.findBySlug(new LessonSlug(slug)).filter(GrammarLesson::isPublished)
                    .orElseThrow(LessonAccess::notFound);
        } catch (DomainException malformed) {
            throw notFound();
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("No such lesson.");
    }
}
