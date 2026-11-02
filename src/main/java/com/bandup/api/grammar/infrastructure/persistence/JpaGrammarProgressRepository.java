package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.aggregate.GrammarProgress;
import com.bandup.api.grammar.domain.progress.repository.GrammarProgressRepository;
import com.bandup.api.grammar.domain.progress.valueobject.CourseLevel;
import com.bandup.api.grammar.domain.progress.valueobject.FinishedLesson;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;

/** {@link GrammarProgressRepository} on JPA; the progress row is locked while it is used. */
@Repository
@RequiredArgsConstructor
class JpaGrammarProgressRepository implements GrammarProgressRepository {

    private final EntityManager em;
    private final Clock clock;
    private final Learners learners;

    @Override
    @Transactional
    public Optional<GrammarProgress> findByLearner(LearnerId learner) {
        GrammarProgressEntity row = lockedRow(learners.userId(learner));
        if (row == null) {
            return Optional.empty();
        }
        Map<LessonId, FinishedLesson> finished = new LinkedHashMap<>();
        row.finished.forEach((lesson, f) -> finished.put(new LessonId(lesson),
                new FinishedLesson(CourseLevel.valueOf(f.level), f.firstFinishedOn, f.timesFinished)));
        return Optional.of(GrammarProgress.restore(learner, row.level == null ? null : CourseLevel.valueOf(row.level),
                finished));
    }

    @Override
    @Transactional
    public void save(GrammarProgress progress) {
        Long userId = learners.userId(progress.learner());
        GrammarProgressEntity row = lockedRow(userId);
        if (row == null) {
            row = new GrammarProgressEntity();
            row.userId = userId;
            row.updatedAt = clock.instant();
            em.persist(row);
        }
        row.level = progress.level().map(CourseLevel::name).orElse(null);
        row.updatedAt = clock.instant();
        Map<LessonId, FinishedLesson> finished = progress.finished();
        // finished lessons are only ever added or updated, never removed
        for (Map.Entry<LessonId, FinishedLesson> entry : finished.entrySet()) {
            FinishedLessonRow f = row.finished.computeIfAbsent(entry.getKey().value(), k -> new FinishedLessonRow());
            f.level = entry.getValue().level().name();
            f.firstFinishedOn = entry.getValue().firstFinishedOn();
            f.timesFinished = entry.getValue().timesFinished();
        }
        em.flush();
    }

    private GrammarProgressEntity lockedRow(Long userId) {
        GrammarProgressEntity row = em.find(GrammarProgressEntity.class, userId);
        if (row != null) {
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        }
        return row;
    }
}
