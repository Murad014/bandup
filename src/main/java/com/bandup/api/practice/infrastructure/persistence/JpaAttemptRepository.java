package com.bandup.api.practice.infrastructure.persistence;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.practice.domain.attempt.aggregate.Attempt;
import com.bandup.api.practice.domain.attempt.repository.AttemptRepository;
import com.bandup.api.practice.domain.attempt.valueobject.AttemptId;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseId;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseRevision;
import com.bandup.api.practice.domain.attempt.valueobject.GradedAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.LearnerId;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionId;
import com.bandup.api.practice.domain.attempt.valueobject.Score;
import com.bandup.api.shared.domain.language.LanguageCode;

/**
 * {@link AttemptRepository} on JPA. An attempt and its answers are inserted
 * together, once (attempts are history and never updated). The learner is
 * stored as {@code app_user.id}, looked up by the public id with plain SQL,
 * so Practice never uses Identity's classes.
 */
@Repository
class JpaAttemptRepository implements AttemptRepository {

    private static final String USER_ID = "select id from app_user where public_id = ?";
    private static final String PUBLIC_ID = "select public_id from app_user where id = ?";

    private final EntityManager entityManager;
    private final JdbcClient jdbc;

    JpaAttemptRepository(EntityManager entityManager, JdbcClient jdbc) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void save(Attempt attempt) {
        if (entityManager.find(AttemptEntity.class, attempt.id().value()) != null) {
            throw new IllegalStateException("Attempt " + attempt.id() + " is already saved; attempts never change");
        }
        AttemptEntity entity = new AttemptEntity();
        entity.id = attempt.id().value();
        entity.userId = attempt.learner().map(this::userId).orElse(null);
        entity.exerciseId = attempt.exercise().value();
        entity.exerciseRevision = attempt.revision().value();
        entity.supportLanguage = attempt.language().value();
        entity.submittedAt = attempt.submittedAt();
        entity.elapsedSeconds = Math.toIntExact(attempt.elapsed().toSeconds());
        entity.score = attempt.score();
        entity.total = attempt.total();
        for (GradedAnswer answer : attempt.answers()) {
            AttemptAnswerRow row = new AttemptAnswerRow();
            row.questionId = answer.question().value();
            row.questionNumber = answer.number();
            row.givenValue = AttemptJson.given(answer.given());
            row.expectedValue = AttemptJson.expected(answer.expected());
            row.pointsAwarded = answer.score().points();
            row.pointsPossible = answer.score().possible();
            entity.answers.add(row);
        }
        entityManager.persist(entity);
        entityManager.flush(); // write now: database rules fail here, and SQL in the same transaction sees it
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Attempt> findById(AttemptId id) {
        return Optional.ofNullable(entityManager.find(AttemptEntity.class, id.value())).map(this::toDomain);
    }

    private Attempt toDomain(AttemptEntity entity) {
        return Attempt.restore(new AttemptId(entity.id), new ExerciseId(entity.exerciseId),
                new ExerciseRevision(entity.exerciseRevision),
                Optional.ofNullable(entity.userId).map(this::learnerId), LanguageCode.of(entity.supportLanguage),
                Duration.ofSeconds(entity.elapsedSeconds), entity.submittedAt,
                entity.answers.stream().map(row -> new GradedAnswer(new QuestionId(row.questionId), row.questionNumber,
                        AttemptJson.given(row.givenValue), AttemptJson.expected(row.expectedValue),
                        new Score(row.pointsAwarded, row.pointsPossible))).toList());
    }

    private Long userId(LearnerId learner) {
        return jdbc.sql(USER_ID).param(learner.value()).query(Long.class).optional()
                .orElseThrow(() -> new IllegalStateException("No user with public id " + learner));
    }

    private LearnerId learnerId(Long userId) {
        return new LearnerId(jdbc.sql(PUBLIC_ID).param(userId).query(UUID.class).single());
    }
}
