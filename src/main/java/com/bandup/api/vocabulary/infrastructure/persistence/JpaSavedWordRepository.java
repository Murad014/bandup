package com.bandup.api.vocabulary.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.vocabulary.domain.saved.aggregate.SavedWord;
import com.bandup.api.vocabulary.domain.saved.repository.SavedWordRepository;
import com.bandup.api.vocabulary.domain.saved.valueobject.Box;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordSource;
import com.bandup.api.vocabulary.domain.saved.valueobject.WordState;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/**
 * {@link SavedWordRepository} on JPA. A saved word is locked while it is
 * answered. The source passage is stored by its id and read back as its slug
 * (plain SQL; Content's classes are never used).
 */
@Repository
@RequiredArgsConstructor
class JpaSavedWordRepository implements SavedWordRepository {

    private final EntityManager em;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Learners learners;

    @Override
    @Transactional
    public Optional<SavedWord> findById(SavedWordId id) {
        SavedWordEntity row = em.find(SavedWordEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
        if (row == null) {
            return Optional.empty();
        }
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(toDomain(row));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedWord> find(LearnerId learner, WordId word) {
        return em.createQuery("select s from SavedWordEntity s where s.userId = :user and s.wordId = :word",
                        SavedWordEntity.class)
                .setParameter("user", learners.userId(learner)).setParameter("word", word.value())
                .getResultStream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedWord> findByWord(WordId word) {
        return em.createQuery("select s from SavedWordEntity s where s.wordId = :word", SavedWordEntity.class)
                .setParameter("word", word.value()).getResultStream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(SavedWord word) {
        SavedWordEntity row = em.find(SavedWordEntity.class, word.id().value());
        boolean isNew = row == null;
        if (isNew) {
            row = new SavedWordEntity();
            row.id = word.id().value();
            row.userId = learners.userId(word.learner());
            row.addedOn = word.addedOn();
            row.createdAt = clock.instant();
            row.sourceExerciseId = word.source().exerciseSlug().flatMap(slug -> jdbc
                    .sql("select id from exercise where slug = ?").param(slug).query(Long.class).optional()).orElse(null);
            row.sourceSentence = word.source().sentenceText().orElse(null);
        }
        row.wordId = word.word().value();
        row.state = word.state().name();
        row.box = word.box().map(b -> (short) b.value()).orElse(null);
        row.dueOn = word.dueOn().orElse(null);
        row.lastAnsweredOn = word.lastAnsweredOn().orElse(null);
        if (isNew) {
            em.persist(row);
        }
        em.flush();
    }

    @Override
    @Transactional
    public void delete(SavedWord word) {
        SavedWordEntity row = em.find(SavedWordEntity.class, word.id().value());
        if (row != null) {
            em.remove(row);
            em.flush();
        }
    }

    private SavedWord toDomain(SavedWordEntity row) {
        String slug = row.sourceExerciseId == null ? null
                : jdbc.sql("select slug from exercise where id = ?").param(row.sourceExerciseId).query(String.class)
                        .optional().orElse(null);
        return SavedWord.restore(new SavedWordId(row.id), learners.learnerId(row.userId), new WordId(row.wordId),
                new WordSource(slug, row.sourceSentence), row.addedOn, WordState.valueOf(row.state),
                row.box == null ? null : new Box(row.box), row.dueOn, row.lastAnsweredOn);
    }
}
