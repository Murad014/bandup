package com.bandup.api.practice.infrastructure.readmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.practice.application.port.GradableExercises;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseId;
import com.bandup.api.practice.domain.attempt.valueobject.ExerciseRevision;
import com.bandup.api.practice.domain.attempt.valueobject.GradableExercise;
import com.bandup.api.practice.domain.attempt.valueobject.GradableQuestion;
import com.bandup.api.practice.domain.attempt.valueobject.GradingRule;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionFamily;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionId;
import com.bandup.api.practice.domain.attempt.valueobject.WordLimit;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/**
 * {@link GradableExercises} with plain SQL over Content's tables: four small
 * queries (exercise, questions in order, answers, explanations). A published
 * exercise whose rows break a grading rule (e.g. "Choose TWO" with three keys)
 * fails loudly instead of being graded wrongly.
 */
@Component
@RequiredArgsConstructor
class JdbcGradableExercises implements GradableExercises {

    static final String EXERCISE = """
            select id, revision from exercise where slug = ? and status = 'PUBLISHED'""";

    static final String QUESTIONS = """
            select q.id, t.family, g.choose_count, g.max_words, g.numbers_allowed, e.evidence_key
            from question q
            join question_group g on g.id = q.group_id
            join question_type t on t.code = g.type_code
            left join passage_evidence e on e.id = q.evidence_id
            where q.exercise_id = ?
            order by g.position, q.position""";

    static final String ANSWERS = """
            select a.question_id, a.value
            from question_answer a join question q on q.id = a.question_id
            where q.exercise_id = ?
            order by a.question_id, a.position""";

    static final String EXPLANATIONS = """
            select x.question_id, x.language_code, x.explanation_html
            from question_explanation x join question q on q.id = x.question_id
            where q.exercise_id = ?""";

    private final JdbcClient jdbc;

    @Override
    public Optional<GradableExercise> findPublished(String slug) {
        return jdbc.sql(EXERCISE).param(slug)
                .query((rs, i) -> new long[] {rs.getLong("id"), rs.getInt("revision")})
                .optional()
                .map(row -> load(row[0], (int) row[1]));
    }

    private GradableExercise load(long exerciseId, int revision) {
        Map<Long, List<String>> answers = new HashMap<>();
        jdbc.sql(ANSWERS).param(exerciseId).query(rs -> {
            answers.computeIfAbsent(rs.getLong("question_id"), k -> new ArrayList<>()).add(rs.getString("value"));
        });
        Map<Long, Map<LanguageCode, String>> explanations = new HashMap<>();
        jdbc.sql(EXPLANATIONS).param(exerciseId).query(rs -> {
            explanations.computeIfAbsent(rs.getLong("question_id"), k -> new LinkedHashMap<>())
                    .put(LanguageCode.of(rs.getString("language_code")), rs.getString("explanation_html"));
        });
        List<GradableQuestion> questions = jdbc.sql(QUESTIONS).param(exerciseId).query((rs, i) -> {
            long id = rs.getLong("id");
            List<String> accepted = answers.getOrDefault(id, List.of());
            int maxWords = rs.getInt("max_words");
            WordLimit limit = rs.wasNull() ? null : new WordLimit(maxWords, rs.getBoolean("numbers_allowed"));
            int chooseCount = rs.getInt("choose_count");
            return new GradableQuestion(new QuestionId(id),
                    rule(QuestionFamily.ofCode(rs.getString("family")), accepted, chooseCount, limit, id),
                    rs.getString("evidence_key"),
                    Translations.of(explanations.getOrDefault(id, Map.of())));
        }).list();
        return new GradableExercise(new ExerciseId(exerciseId), new ExerciseRevision(revision), questions);
    }

    private static GradingRule rule(QuestionFamily family, List<String> accepted, int chooseCount, WordLimit limit,
            long questionId) {
        if (accepted.isEmpty()) {
            throw new IllegalStateException("Published question " + questionId + " has no answer");
        }
        return switch (family) {
            case CHOICE_FIXED -> GradingRule.choiceFixed(only(accepted, questionId));
            case MC_SINGLE -> GradingRule.mcSingle(only(accepted, questionId));
            case MATCH -> GradingRule.match(only(accepted, questionId));
            case MC_MULTI -> GradingRule.mcMulti(accepted, chooseCount);
            case GAP -> GradingRule.gap(accepted, limit);
        };
    }

    private static String only(List<String> accepted, long questionId) {
        if (accepted.size() != 1) {
            throw new IllegalStateException("Published question " + questionId + " needs exactly one answer, has " + accepted);
        }
        return accepted.getFirst();
    }
}
