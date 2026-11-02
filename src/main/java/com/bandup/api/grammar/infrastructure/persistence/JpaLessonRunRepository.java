package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.run.aggregate.LessonRun;
import com.bandup.api.grammar.domain.run.repository.LessonRunRepository;
import com.bandup.api.grammar.domain.run.valueobject.LessonRevision;
import com.bandup.api.grammar.domain.run.valueobject.RunId;

/** {@link LessonRunRepository} on JPA; a run is locked while an answer is recorded. */
@Repository
@RequiredArgsConstructor
class JpaLessonRunRepository implements LessonRunRepository {

    private final EntityManager em;
    private final Learners learners;

    @Override
    @Transactional
    public Optional<LessonRun> findById(RunId id) {
        LessonRunEntity row = em.find(LessonRunEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            return Optional.empty();
        }
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(LessonRun.restore(new RunId(row.id), new LessonId(row.lessonId),
                new LessonRevision(row.lessonRevision), row.userId == null ? null : learners.learnerId(row.userId),
                row.startedAt, row.finishedAt, new LinkedHashSet<>(LessonJson.ids(row.mastered)), row.retries,
                row.answered));
    }

    @Override
    @Transactional
    public void save(LessonRun run) {
        LessonRunEntity row = em.find(LessonRunEntity.class, run.id().value());
        if (row == null) {
            row = new LessonRunEntity();
            row.id = run.id().value();
            row.lessonId = run.lesson().value();
            row.lessonRevision = run.lessonRevision().value();
            row.userId = run.learner().map(learners::userId).orElse(null);
            row.startedAt = run.startedAt();
            copy(run, row);
            em.persist(row);
        } else {
            copy(run, row);
        }
        em.flush();
    }

    private static void copy(LessonRun run, LessonRunEntity row) {
        row.finishedAt = run.finishedAt().orElse(null);
        row.mastered = LessonJson.ids(run.mastered());
        row.retries = run.retries();
        row.answered = run.answered();
    }

    @Override
    @Transactional
    public int deleteUnfinishedStartedBefore(Instant cutoff) {
        return em.createQuery("delete from LessonRunEntity r where r.finishedAt is null and r.startedAt < :cutoff")
                .setParameter("cutoff", cutoff).executeUpdate();
    }
}
