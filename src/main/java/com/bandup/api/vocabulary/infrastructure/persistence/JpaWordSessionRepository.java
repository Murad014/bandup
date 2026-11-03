package com.bandup.api.vocabulary.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.vocabulary.domain.session.aggregate.WordSession;
import com.bandup.api.vocabulary.domain.session.repository.WordSessionRepository;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;

/** {@link WordSessionRepository} on JPA; a session is locked while an answer is graded. */
@Repository
@RequiredArgsConstructor
class JpaWordSessionRepository implements WordSessionRepository {

    private final EntityManager em;
    private final Learners learners;

    @Override
    @Transactional
    public Optional<WordSession> findById(SessionId id) {
        WordSessionEntity row = em.find(WordSessionEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            return Optional.empty();
        }
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        SessionJson.Content c = SessionJson.read(row.content);
        return Optional.of(WordSession.restore(new SessionId(row.id), learners.learnerId(row.userId),
                SessionKind.valueOf(row.kind), c.words(), c.tasks(), c.queue(), c.mastered(), row.retries,
                row.startedAt, row.finishedAt));
    }

    @Override
    @Transactional
    public void save(WordSession session) {
        WordSessionEntity row = em.find(WordSessionEntity.class, session.id().value());
        boolean isNew = row == null;
        if (isNew) {
            row = new WordSessionEntity();
            row.id = session.id().value();
            row.userId = learners.userId(session.learner());
            row.kind = session.kind().name();
            row.startedAt = session.startedAt();
        }
        row.content = SessionJson.write(session.words(), session.tasks(), session.queue(), session.mastered());
        row.retries = session.retries();
        row.finishedAt = session.finishedAt().orElse(null);
        if (isNew) {
            em.persist(row);
        }
        em.flush();
    }

    @Override
    @Transactional
    public int deleteStartedBefore(Instant cutoff) {
        return em.createQuery("delete from WordSessionEntity s where s.startedAt < :cutoff")
                .setParameter("cutoff", cutoff).executeUpdate();
    }
}
