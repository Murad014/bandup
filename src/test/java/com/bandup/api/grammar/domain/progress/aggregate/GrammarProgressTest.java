package com.bandup.api.grammar.domain.progress.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.FinishedLesson;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;

class GrammarProgressTest {

    static final LearnerId ANNA = new LearnerId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    static final LocalDate TUESDAY = MONDAY.plusDays(1);

    @Test
    void aNewLearnerHasNoLevelAndNothingFinished() {
        GrammarProgress progress = GrammarProgress.start(ANNA);
        assertThat(progress.level()).isEmpty();
        assertThat(progress.finished()).isEmpty();
        assertThat(progress.mayStartNewLesson(CourseLevel.B1, MONDAY)).isTrue();
    }

    @Test
    void theLevelCanChangeAndProgressInEachLevelIsKept() {
        GrammarProgress progress = GrammarProgress.start(ANNA);
        progress.chooseLevel(CourseLevel.B1);
        progress.finish(new LessonId(1), CourseLevel.B1, MONDAY);
        progress.chooseLevel(CourseLevel.C1);
        assertThat(progress.level()).contains(CourseLevel.C1);
        assertThat(progress.hasFinished(new LessonId(1))).isTrue();
    }

    @Test
    void onlyTheFirstFinishIsANewLessonAndReplaysAreCounted() {
        GrammarProgress progress = GrammarProgress.start(ANNA);
        assertThat(progress.finish(new LessonId(1), CourseLevel.B1, MONDAY)).isTrue();
        assertThat(progress.finish(new LessonId(1), CourseLevel.B1, TUESDAY)).isFalse();
        assertThat(progress.finished().get(new LessonId(1))).isEqualTo(new FinishedLesson(CourseLevel.B1, MONDAY, 2));
    }

    @Test
    void oneNewLessonPerLevelAndDay() {
        GrammarProgress progress = GrammarProgress.start(ANNA);
        progress.finish(new LessonId(1), CourseLevel.B1, MONDAY);
        assertThat(progress.mayStartNewLesson(CourseLevel.B1, MONDAY)).isFalse();
        assertThat(progress.mayStartNewLesson(CourseLevel.B1, TUESDAY)).isTrue();
        assertThat(progress.mayStartNewLesson(CourseLevel.C1, MONDAY)).as("another level has its own limit").isTrue();
        progress.finish(new LessonId(1), CourseLevel.B1, TUESDAY); // a replay
        assertThat(progress.mayStartNewLesson(CourseLevel.B1, TUESDAY)).as("replays don't count").isTrue();
    }
}
