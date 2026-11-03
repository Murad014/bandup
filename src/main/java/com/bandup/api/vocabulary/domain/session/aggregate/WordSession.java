package com.bandup.api.vocabulary.domain.session.aggregate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.session.valueobject.GivenAnswer;
import com.bandup.api.vocabulary.domain.session.valueobject.Grade;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.Task;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskFeedback;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskId;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskPhase;

/**
 * One learn or review session (aggregate root; vocabulary requirements 5, 6).
 * Holds snapshots of its words and the tasks built from them, and grades
 * every answer on the server. Practise tasks are answered once; a wrong master
 * task goes to the back of the queue until it is right. The session is
 * finished when every master task is mastered.
 */
@AggregateRoot
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class WordSession {

    @EqualsAndHashCode.Include
    @Getter
    private final SessionId id;
    @Getter
    private final LearnerId learner;
    @Getter
    private final SessionKind kind;
    @Getter
    private final List<SessionWord> words;
    @Getter
    private final List<Task> tasks;
    private final List<TaskId> queue;
    private final Set<TaskId> mastered;
    @Getter
    private int retries;
    @Getter
    private final Instant startedAt;
    private Instant finishedAt;

    private WordSession(SessionId id, LearnerId learner, SessionKind kind, List<SessionWord> words, List<Task> tasks,
            Collection<TaskId> queue, Collection<TaskId> mastered, int retries, Instant startedAt, Instant finishedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.learner = Objects.requireNonNull(learner, "learner");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.words = List.copyOf(words);
        this.tasks = List.copyOf(tasks);
        this.queue = new ArrayList<>(queue);
        this.mastered = new LinkedHashSet<>(mastered);
        this.retries = retries;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = finishedAt;
    }

    public static WordSession start(SessionId id, LearnerId learner, SessionKind kind, List<SessionWord> words,
            List<Task> tasks, Instant now) {
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("A session needs tasks");
        }
        return new WordSession(id, learner, kind, words, tasks, tasks.stream().map(Task::id).toList(), List.of(), 0,
                now, null);
    }

    public static WordSession restore(SessionId id, LearnerId learner, SessionKind kind, List<SessionWord> words,
            List<Task> tasks, List<TaskId> queue, Collection<TaskId> mastered, int retries, Instant startedAt,
            Instant finishedAt) {
        return new WordSession(id, learner, kind, words, tasks, queue, mastered, retries, startedAt, finishedAt);
    }

    /**
     * Grades one answer.
     *
     * @throws DomainException (field {@code taskId}) for a task that isn't waiting; (field {@code answer})
     *                         for an answer of the wrong kind
     * @throws IllegalStateException when the session is finished
     */
    public TaskFeedback answer(TaskId taskId, GivenAnswer answer, UnaryOperator<String> normalise, Instant now) {
        if (isFinished()) {
            throw new IllegalStateException("The session is finished");
        }
        if (!queue.contains(taskId)) {
            throw DomainException.of("taskId", "This task isn't waiting for an answer.");
        }
        Task task = task(taskId).orElseThrow();
        Grade grade = task.grade(answer, normalise);
        queue.remove(taskId);
        if (task.phase() == TaskPhase.MASTER) {
            if (grade.correct()) {
                mastered.add(taskId);
            } else {
                queue.add(taskId);
                retries++;
            }
        }
        if (queue.isEmpty()) {
            finishedAt = now;
        }
        return new TaskFeedback(taskId, task.word(), task.phase(), grade.correct(), grade.expected(), grade.why(),
                explanation(task), mastered.size(), masterTotal(), isFinished());
    }

    private String explanation(Task task) {
        if (task.word() == null) {
            return "";
        }
        return words.stream().filter(w -> w.saved().equals(task.word())).findFirst()
                .map(w -> w.word() + " — " + w.meaningEn()).orElse("");
    }

    public int masterTotal() {
        return (int) tasks.stream().filter(t -> t.phase() == TaskPhase.MASTER).count();
    }

    public Optional<Task> task(TaskId taskId) {
        return tasks.stream().filter(t -> t.id().equals(taskId)).findFirst();
    }

    public List<TaskId> queue() {
        return Collections.unmodifiableList(queue);
    }

    public Set<TaskId> mastered() {
        return Collections.unmodifiableSet(mastered);
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public boolean belongsTo(LearnerId caller) {
        return learner.equals(caller);
    }
}
