package com.bandup.api.grammar.domain.progress.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.progress.valueobject.LessonState;
import com.bandup.api.grammar.domain.progress.valueobject.LessonState.LockReason;

class CourseUnlockingTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LessonId L1 = new LessonId(1), L2 = new LessonId(2), L3 = new LessonId(3), L9 = new LessonId(9);
    private static final List<LessonId> COURSE = List.of(L1, L2, L3);

    private final CourseUnlocking unlocking = new CourseUnlocking();

    private static GrammarProgress anna() {
        return GrammarProgress.start(new LearnerId(UUID.randomUUID()));
    }

    private List<LessonState> states(Optional<GrammarProgress> progress, Optional<LessonId> firstUnfinished,
            LocalDate today) {
        return unlocking.states(COURSE, progress, CourseLevel.B1, firstUnfinished, Optional.of(L1), today);
    }

    @Test
    void aNewLearnerMayStartTheFirstLessonOnly() {
        assertThat(states(Optional.of(anna()), Optional.of(L1), MONDAY)).containsExactly(LessonState.next(L1),
                LessonState.locked(L2, LockReason.FINISH_PREVIOUS), LessonState.locked(L3, LockReason.FINISH_PREVIOUS));
    }

    @Test
    void afterTodaysNewLessonTheNextWaitsUntilTomorrowAndFinishedOnesStayOpen() {
        GrammarProgress progress = anna();
        progress.finish(L1, CourseLevel.B1, MONDAY);
        assertThat(states(Optional.of(progress), Optional.of(L2), MONDAY)).containsExactly(LessonState.done(L1),
                LessonState.locked(L2, LockReason.TOMORROW), LessonState.locked(L3, LockReason.FINISH_PREVIOUS));
        assertThat(states(Optional.of(progress), Optional.of(L2), TUESDAY)).containsExactly(LessonState.done(L1),
                LessonState.next(L2), LessonState.locked(L3, LockReason.FINISH_PREVIOUS));
    }

    @Test
    void aLessonInsertedBeforeTheLearnersPlaceBecomesTheirNextOne() {
        GrammarProgress progress = anna();
        progress.finish(L1, CourseLevel.B1, MONDAY);
        progress.finish(L3, CourseLevel.B1, MONDAY.minusDays(1));
        // L2 was published later between L1 and L3
        assertThat(states(Optional.of(progress), Optional.of(L2), TUESDAY)).containsExactly(LessonState.done(L1),
                LessonState.next(L2), LessonState.done(L3));
    }

    @Test
    void whenEverythingIsFinishedAllLessonsAreDone() {
        GrammarProgress progress = anna();
        COURSE.forEach(l -> progress.finish(l, CourseLevel.B1, MONDAY.minusDays(l.value())));
        assertThat(states(Optional.of(progress), Optional.empty(), MONDAY)).extracting(LessonState::state)
                .containsOnly(LessonState.State.DONE);
    }

    @Test
    void guestsMayOpenOnlyTheCoursesFirstLesson() {
        assertThat(states(Optional.empty(), Optional.empty(), MONDAY)).containsExactly(LessonState.next(L1),
                LessonState.locked(L2, LockReason.SIGN_IN), LessonState.locked(L3, LockReason.SIGN_IN));
        assertThat(unlocking.stateOf(L9, Optional.empty(), CourseLevel.B1, Optional.empty(), Optional.empty(), MONDAY))
                .isEqualTo(LessonState.locked(L9, LockReason.SIGN_IN));
    }

    @Test
    void onlyLockedStatesCarryAReason() {
        assertThat(LessonState.next(L1).canOpen()).isTrue();
        assertThat(LessonState.done(L1).canOpen()).isTrue();
        assertThat(LessonState.locked(L1, LockReason.TOMORROW).canOpen()).isFalse();
        assertThat(LessonState.locked(L1, LockReason.TOMORROW).lockReason()).contains(LockReason.TOMORROW);
    }
}
