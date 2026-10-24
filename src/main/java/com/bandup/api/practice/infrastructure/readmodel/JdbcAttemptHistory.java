package com.bandup.api.practice.infrastructure.readmodel;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.practice.application.port.AttemptHistory;
import com.bandup.api.shared.domain.language.LanguageCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AttemptHistory} with explicit SQL. Every query is limited to the
 * learner's own attempts ({@code app_user.public_id}); anonymous attempts have
 * no user and never match.
 */
@Component
@RequiredArgsConstructor
class JdbcAttemptHistory implements AttemptHistory {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String MINE = """
            from attempt a
            join app_user u on u.id = a.user_id and u.public_id = :learner
            join exercise x on x.id = a.exercise_id""";

    private final JdbcClient jdbc;

    @Override
    public Totals totals(UUID learner) {
        return jdbc.sql("""
                select count(*) as attempts, count(distinct a.exercise_id) as passages,
                       coalesce(sum(a.elapsed_seconds), 0) as seconds,
                       round(avg(round(100.0 * a.score / nullif(a.total, 0)))) as average,
                       max(round(100.0 * a.score / nullif(a.total, 0))) as best
                """ + MINE)
                .param("learner", learner)
                .query((rs, i) -> new Totals(rs.getInt("attempts"), rs.getInt("passages"), rs.getLong("seconds"),
                        integer(rs, "average"), integer(rs, "best")))
                .single();
    }

    @Override
    public List<Passage> attemptedPassages(UUID learner) {
        return jdbc.sql("""
                select x.slug, x.title, max(a.submitted_at) as latest
                """ + MINE + """

                group by x.slug, x.title
                order by latest desc""")
                .param("learner", learner)
                .query((rs, i) -> new Passage(rs.getString("slug"), rs.getString("title")))
                .list();
    }

    @Override
    public long count(UUID learner, String slug) {
        return jdbc.sql("select count(*) " + MINE + " where (cast(:slug as text) is null or x.slug = :slug)")
                .param("learner", learner).param("slug", slug)
                .query(Long.class).single();
    }

    @Override
    public List<Item> page(UUID learner, String slug, int offset, int limit) {
        return jdbc.sql("""
                select a.id, x.slug, x.title, x.status, a.submitted_at, a.score, a.total, a.elapsed_seconds,
                       (select '/media/' || m.public_id from media m where m.id = x.cover_media_id) as cover_url,
                       array(select t.slug from exercise_topic et join topic t on t.id = et.topic_id
                             where et.exercise_id = x.id order by t.position, t.slug) as topics
                """ + MINE + """

                where (cast(:slug as text) is null or x.slug = :slug)
                order by a.submitted_at desc, a.id
                offset :offset limit :limit""")
                .param("learner", learner).param("slug", slug).param("offset", offset).param("limit", limit)
                .query((rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                        rs.getString("cover_url"), strings(rs.getArray("topics")), "PUBLISHED".equals(rs.getString("status")),
                        rs.getTimestamp("submitted_at").toInstant(), rs.getInt("score"), rs.getInt("total"),
                        rs.getInt("elapsed_seconds")))
                .list();
    }

    @Override
    public Optional<Stored> find(UUID learner, UUID attemptId) {
        Optional<Stored> head = jdbc.sql("""
                select a.id, x.slug, x.title, x.status, a.exercise_revision, x.revision, a.submitted_at,
                       a.score, a.total, a.elapsed_seconds
                """ + MINE + """

                where a.id = :id""")
                .param("learner", learner).param("id", attemptId)
                .query((rs, i) -> new Stored(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("title"),
                        "PUBLISHED".equals(rs.getString("status")), rs.getInt("exercise_revision"), rs.getInt("revision"),
                        rs.getTimestamp("submitted_at").toInstant(), rs.getInt("score"), rs.getInt("total"),
                        rs.getInt("elapsed_seconds"), List.of()))
                .optional();
        return head.map(h -> new Stored(h.attemptId(), h.slug(), h.title(), h.available(), h.attemptRevision(),
                h.currentRevision(), h.submittedAt(), h.score(), h.total(), h.elapsedSeconds(), answers(attemptId)));
    }

    private List<StoredAnswer> answers(UUID attemptId) {
        Map<Long, Map<LanguageCode, String>> explanations = new HashMap<>();
        jdbc.sql("""
                select x.question_id, x.language_code, x.explanation_html
                from question_explanation x join attempt_answer aa on aa.question_id = x.question_id
                where aa.attempt_id = ?""")
                .param(attemptId)
                .query((ResultSet rs) -> {
                    explanations.computeIfAbsent(rs.getLong("question_id"), k -> new LinkedHashMap<>())
                            .put(LanguageCode.of(rs.getString("language_code")), rs.getString("explanation_html"));
                });
        return jdbc.sql("""
                select aa.question_id, aa.question_number, aa.given_value::text as given, aa.expected_value::text as expected,
                       aa.points_awarded, aa.points_possible, t.family, g.choose_count, e.evidence_key
                from attempt_answer aa
                join question q on q.id = aa.question_id
                join question_group g on g.id = q.group_id
                join question_type t on t.code = g.type_code
                left join passage_evidence e on e.id = q.evidence_id
                where aa.attempt_id = ?
                order by aa.question_number""")
                .param(attemptId)
                .query((rs, i) -> new StoredAnswer(rs.getInt("question_number"), given(rs.getString("given")),
                        strings(JSON.readTree(rs.getString("expected"))), rs.getInt("points_awarded"),
                        rs.getInt("points_possible"), rs.getString("family"), integer(rs, "choose_count"),
                        rs.getString("evidence_key"), explanations.getOrDefault(rs.getLong("question_id"), Map.of())))
                .list();
    }

    /** A stored text answer is a JSON string, "Choose N" keys a JSON array. */
    private static Object given(String json) {
        JsonNode node = JSON.readTree(json);
        return node.isArray() ? strings(node) : node.asString();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number n = (Number) rs.getObject(column);
        return n == null ? null : n.intValue();
    }
}
