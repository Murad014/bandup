package com.bandup.api.grammar.domain.run.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.run.valueobject.LessonRevision;
import com.bandup.api.grammar.domain.run.valueobject.RunOutcome;

class LessonRunTest {

    private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-02T08:20:00Z");
    private static final LearnerId ANNA = new LearnerId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final LearnerId BOB = new LearnerId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    private static ItemId c(int n) {
        return new ItemId("c" + n);
    }

    private LessonRun annasRun() {
        return LessonRun.start(new LessonId(7), new LessonRevision(2), Optional.of(ANNA), START);
    }

    @Test
    void startsEmptyWithTheLessonRevision() {
        LessonRun run = annasRun();
        assertThat(run.lessonRevision()).isEqualTo(new LessonRevision(2));
        assertThat(run.isFinished()).isFalse();
        assertThat(run.mastered()).isEmpty();
        assertThat(run.retries()).isZero();
        assertThat(run.startedAt()).isEqualTo(START);
    }

    @Test
    void practiceItemsCountButNeverMasterOrRetry() {
        LessonRun run = annasRun();
        assertThat(run.record(new ItemId("p1"), false, false, 2, START)).isEqualTo(new RunOutcome(0, 2, 0, false, false));
        run.record(new ItemId("p2"), true, false, 2, START);
        assertThat(run.answered()).isEqualTo(2);
        assertThat(run.mastered()).isEmpty();
    }

    @Test
    void aWrongCheckItemIsARetryAndTheRunFinishesWhenEveryCheckItemIsMastered() {
        LessonRun run = annasRun();
        assertThat(run.record(c(1), false, true, 2, START)).isEqualTo(new RunOutcome(0, 2, 1, false, false));
        assertThat(run.record(c(2), true, true, 2, START)).isEqualTo(new RunOutcome(1, 2, 1, false, false));
        assertThat(run.record(c(1), true, true, 2, LATER)).isEqualTo(new RunOutcome(2, 2, 1, true, true));
        assertThat(run.finishedAt()).contains(LATER);
    }

    @Test
    void masteringTheSameItemTwiceCountsOnce() {
        LessonRun run = annasRun();
        run.record(c(1), true, true, 2, START);
        assertThat(run.record(c(1), true, true, 2, START).finished()).isFalse();
        assertThat(run.mastered()).containsExactly(c(1));
    }

    @Test
    void aFinishedRunTakesNoMoreAnswers() {
        LessonRun run = annasRun();
        run.record(c(1), true, true, 1, START);
        assertThatThrownBy(() -> run.record(c(1), true, true, 1, LATER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aLearnersRunBelongsOnlyToThemAGuestRunToWhoeverHasItsId() {
        assertThat(annasRun().belongsTo(Optional.of(ANNA))).isTrue();
        assertThat(annasRun().belongsTo(Optional.of(BOB))).isFalse();
        assertThat(annasRun().belongsTo(Optional.empty())).isFalse();
        LessonRun guest = LessonRun.start(new LessonId(7), new LessonRevision(1), Optional.empty(), START);
        assertThat(guest.belongsTo(Optional.empty())).isTrue();
        assertThat(guest.belongsTo(Optional.of(BOB))).isTrue();
        assertThat(guest.learner()).isEmpty();
    }

    @Test
    void everyRunGetsItsOwnRandomId() {
        assertThat(annasRun().id()).isNotEqualTo(annasRun().id());
        assertThat(annasRun()).isNotEqualTo(annasRun());
    }
}
