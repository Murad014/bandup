package com.bandup.api.content.infrastructure.readmodel;

import java.util.HashSet;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.AttemptsLookup;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;

/** {@link AttemptsLookup} with SQL on Practice's {@code attempt} tables (no Practice classes). */
@Component
class JdbcAttemptsLookup implements AttemptsLookup {

    private final JdbcClient jdbc;

    JdbcAttemptsLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasAttempts(ExerciseId exercise) {
        return jdbc.sql("select exists (select 1 from attempt where exercise_id = ?)").param(exercise.value())
                .query(Boolean.class).single();
    }

    @Override
    public Set<QuestionId> answeredQuestions(ExerciseId exercise) {
        Set<QuestionId> answered = new HashSet<>();
        jdbc.sql("""
                select distinct aa.question_id from attempt_answer aa
                join question q on q.id = aa.question_id
                where q.exercise_id = ?""").param(exercise.value())
                .query(rs -> {
                    answered.add(new QuestionId(rs.getLong(1)));
                });
        return answered;
    }
}
