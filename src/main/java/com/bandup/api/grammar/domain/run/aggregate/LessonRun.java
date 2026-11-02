package com.bandup.api.grammar.domain.run.aggregate;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.run.valueobject.LessonRevision;
import com.bandup.api.grammar.domain.run.valueobject.RunId;
import com.bandup.api.grammar.domain.run.valueobject.RunOutcome;
import com.bandup.api.shared.domain.ddd.AggregateRoot;

/**
 * One pass of a learner (or a guest) through one lesson (aggregate root;
 * grammar requirements 4.5, 8.2, 8.3). The browser keeps the question queue;
 * the run keeps what counts: which check items are mastered, the retries,
 * and when the lesson was finished (once every check item is mastered).
 * Practice items are counted but never needed.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class LessonRun {

    @EqualsAndHashCode.Include
    @Getter
    private final RunId id;
    @Getter
    private final LessonId lesson;
    @Getter
    private final LessonRevision lessonRevision;
    private final LearnerId learner;
    @Getter
    private final Instant startedAt;
    private Instant finishedAt;
    private final Set<ItemId> mastered;
    @Getter
    private int retries;
    @Getter
    private int answered;

    private LessonRun(RunId id, LessonId lesson, LessonRevision lessonRevision, LearnerId learner, Instant startedAt,
            Instant finishedAt, Set<ItemId> mastered, int retries, int answered) {
        this.id = Objects.requireNonNull(id, "id");
        this.lesson = Objects.requireNonNull(lesson, "lesson");
        this.lessonRevision = Objects.requireNonNull(lessonRevision, "lessonRevision");
        this.learner = learner;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = finishedAt;
        this.mastered = new LinkedHashSet<>(mastered);
        if (retries < 0 || answered < 0) {
            throw new IllegalArgumentException("Counts can't be negative");
        }
        this.retries = retries;
        this.answered = answered;
    }

    /** @param learner empty for a guest */
    public static LessonRun start(LessonId lesson, LessonRevision revision, Optional<LearnerId> learner, Instant now) {
        return new LessonRun(RunId.random(), lesson, revision, learner.orElse(null), now, null, Set.of(), 0, 0);
    }

    public static LessonRun restore(RunId id, LessonId lesson, LessonRevision revision, LearnerId learner,
            Instant startedAt, Instant finishedAt, Set<ItemId> mastered, int retries, int answered) {
        return new LessonRun(id, lesson, revision, learner, startedAt, finishedAt, mastered, retries, answered);
    }

    /**
     * Records one graded answer.
     *
     * @param checkItem  the item is one of the lesson's check items
     * @param checkTotal how many check items the lesson has
     * @throws IllegalStateException if the run is already finished (the caller refuses that first)
     */
    public RunOutcome record(ItemId item, boolean correct, boolean checkItem, int checkTotal, Instant now) {
        if (isFinished()) {
            throw new IllegalStateException("Run " + id + " is already finished");
        }
        answered++;
        boolean justFinished = false;
        if (checkItem) {
            if (correct) {
                mastered.add(item);
                if (mastered.size() >= checkTotal) {
                    finishedAt = now;
                    justFinished = true;
                }
            } else {
                retries++;
            }
        }
        return new RunOutcome(mastered.size(), checkTotal, retries, isFinished(), justFinished);
    }

    /**
     * Whether {@code caller} may use this run: a learner's run only by that
     * learner, a guest's run by whoever holds its id.
     */
    public boolean belongsTo(Optional<LearnerId> caller) {
        return learner == null || caller.filter(learner::equals).isPresent();
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    public Optional<LearnerId> learner() {
        return Optional.ofNullable(learner);
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Set<ItemId> mastered() {
        return Set.copyOf(mastered);
    }
}
