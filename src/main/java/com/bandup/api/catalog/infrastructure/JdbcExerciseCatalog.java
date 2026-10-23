package com.bandup.api.catalog.infrastructure;

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

import com.bandup.api.catalog.application.CatalogFilters;
import com.bandup.api.catalog.application.CatalogPage;
import com.bandup.api.catalog.application.CatalogQuery;
import com.bandup.api.catalog.application.ExerciseCard;
import com.bandup.api.catalog.application.port.ExerciseCatalog;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/**
 * {@link ExerciseCatalog} in plain SQL. Only PUBLISHED exercises; never reads
 * {@code question_answer} or {@code question_explanation}. One query for the
 * page (filters built from the query, values always bound as parameters),
 * then small queries for the cards on that page only.
 */
@Component
@RequiredArgsConstructor
class JdbcExerciseCatalog implements ExerciseCatalog {

    private final JdbcClient jdbc;

    private record Row(long id, String slug, String title, String skill, String format, String level, Integer duration,
            String coverUrl) {
    }

    @Override
    public CatalogPage find(CatalogQuery q, Optional<UUID> learner, LanguageChain help) {
        Optional<Long> userId = learner.flatMap(this::userId);
        StringBuilder where = new StringBuilder(" from exercise x where x.status = 'PUBLISHED'");
        Map<String, Object> params = new HashMap<>();
        if (q.skill() != null) {
            where.append(" and x.skill = :skill");
            params.put("skill", q.skill());
        }
        if (q.format() != null) {
            where.append(" and x.format = :format");
            params.put("format", q.format());
        }
        if (!q.levels().isEmpty()) {
            where.append(" and x.level in (:levels)");
            params.put("levels", q.levels());
        }
        if (!q.types().isEmpty()) {
            where.append(" and exists (select 1 from question_group g where g.exercise_id = x.id and g.type_code in (:types))");
            params.put("types", q.types());
        }
        if (!q.topics().isEmpty()) {
            where.append(" and exists (select 1 from exercise_topic et join topic t on t.id = et.topic_id"
                    + " where et.exercise_id = x.id and t.slug in (:topics))");
            params.put("topics", q.topics());
        }
        if (q.maxMinutes() != null) {
            where.append(" and (x.duration_seconds is null or x.duration_seconds <= :maxSeconds)");
            params.put("maxSeconds", q.maxMinutes() * 60);
        }
        if (q.notDone() && userId.isPresent()) {
            where.append(" and not exists (select 1 from attempt a where a.exercise_id = x.id and a.user_id = :userId)");
            params.put("userId", userId.get());
        }

        long total = jdbc.sql("select count(*)" + where).params(params).query(Long.class).single();
        params.put("limit", q.size());
        params.put("offset", (long) q.page() * q.size());
        List<Row> rows = jdbc.sql("select x.id, x.slug, x.title, x.skill, x.format, x.level, x.duration_seconds,"
                        + " (select '/media/' || m.public_id from media m where m.id = x.cover_media_id) as cover_url" + where
                        + " order by x.published_at desc nulls last, x.title limit :limit offset :offset")
                .params(params)
                .query((rs, i) -> new Row(rs.getLong("id"), rs.getString("slug"), rs.getString("title"), rs.getString("skill"),
                        rs.getString("format"), rs.getString("level"), rs.getObject("duration_seconds", Integer.class),
                        rs.getString("cover_url")))
                .list();
        return new CatalogPage(cards(rows, userId, help), q.page(), q.size(), total);
    }

    private List<ExerciseCard> cards(List<Row> rows, Optional<Long> userId, LanguageChain help) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Row::id).toList();
        Map<Long, Integer> numbers = new HashMap<>();
        jdbc.sql("""
                select q.exercise_id, sum(case when t.family = 'mcMulti' then coalesce(g.choose_count, 1) else 1 end) as n
                from question q join question_group g on g.id = q.group_id join question_type t on t.code = g.type_code
                where q.exercise_id in (:ids) group by q.exercise_id""").param("ids", ids)
                .query(rs -> {
                    numbers.put(rs.getLong(1), rs.getInt(2));
                });
        Map<Long, List<String>> types = new HashMap<>();
        jdbc.sql("select exercise_id, type_code from question_group where exercise_id in (:ids) order by position")
                .param("ids", ids).query(rs -> {
                    List<String> list = types.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>());
                    if (!list.contains(rs.getString(2))) {
                        list.add(rs.getString(2));
                    }
                });
        Map<Long, List<ExerciseCard.Topic>> topics = topicsOf(ids, help);
        Map<Long, ExerciseCard.Score> best = new HashMap<>();
        userId.ifPresent(uid -> jdbc.sql("""
                select exercise_id, score, total from attempt
                where user_id = :uid and exercise_id in (:ids) and total > 0""")
                .param("uid", uid).param("ids", ids).query(rs -> {
                    ExerciseCard.Score score = new ExerciseCard.Score(rs.getInt("score"), rs.getInt("total"));
                    best.merge(rs.getLong("exercise_id"), score,
                            (a, b) -> (double) b.score() / b.total() > (double) a.score() / a.total() ? b : a);
                }));
        return rows.stream().map(r -> new ExerciseCard(r.slug(), r.title(), r.skill(), r.format(), r.level(), r.duration(),
                numbers.getOrDefault(r.id(), 0), types.getOrDefault(r.id(), List.of()), topics.getOrDefault(r.id(), List.of()),
                best.containsKey(r.id()), best.get(r.id()), r.coverUrl())).toList();
    }

    private Map<Long, List<ExerciseCard.Topic>> topicsOf(List<Long> ids, LanguageChain help) {
        Map<Long, Map<String, Map<LanguageCode, String>>> names = new LinkedHashMap<>();
        jdbc.sql("""
                select et.exercise_id, t.slug, tt.language_code, tt.name
                from exercise_topic et join topic t on t.id = et.topic_id
                left join topic_translation tt on tt.topic_id = t.id
                where et.exercise_id in (:ids) order by t.position, t.slug""").param("ids", ids)
                .query(rs -> {
                    Map<LanguageCode, String> byLanguage = names.computeIfAbsent(rs.getLong(1), k -> new LinkedHashMap<>())
                            .computeIfAbsent(rs.getString(2), k -> new LinkedHashMap<>());
                    if (rs.getString(3) != null) {
                        byLanguage.put(LanguageCode.of(rs.getString(3)), rs.getString(4));
                    }
                });
        Map<Long, List<ExerciseCard.Topic>> result = new HashMap<>();
        names.forEach((exercise, bySlug) -> result.put(exercise, bySlug.entrySet().stream()
                .map(e -> new ExerciseCard.Topic(e.getKey(), help.pick(Translations.of(e.getValue())).orElse(e.getKey())))
                .toList()));
        return result;
    }

    @Override
    public CatalogFilters filters(LanguageChain help) {
        String published = "from exercise x where x.status = 'PUBLISHED'";
        List<String> skills = jdbc.sql("select distinct x.skill " + published + " order by 1").query(String.class).list();
        List<String> formats = jdbc.sql("select distinct x.format " + published + " order by 1").query(String.class).list();
        List<String> levels = jdbc.sql("select distinct x.level " + published + " and x.level is not null order by 1")
                .query(String.class).list();
        List<String> types = jdbc.sql("""
                select g.type_code from question_group g join exercise x on x.id = g.exercise_id
                where x.status = 'PUBLISHED' group by g.type_code order by min(g.position), g.type_code""")
                .query(String.class).list();
        Map<String, Map<LanguageCode, String>> names = new LinkedHashMap<>();
        jdbc.sql("""
                select t.slug, tt.language_code, tt.name from topic t left join topic_translation tt on tt.topic_id = t.id
                where exists (select 1 from exercise_topic et join exercise x on x.id = et.exercise_id
                              where et.topic_id = t.id and x.status = 'PUBLISHED')
                order by t.position, t.slug""").query(rs -> {
                    Map<LanguageCode, String> byLanguage = names.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>());
                    if (rs.getString(2) != null) {
                        byLanguage.put(LanguageCode.of(rs.getString(2)), rs.getString(3));
                    }
                });
        List<ExerciseCard.Topic> topics = names.entrySet().stream()
                .map(e -> new ExerciseCard.Topic(e.getKey(), help.pick(Translations.of(e.getValue())).orElse(e.getKey())))
                .toList();
        return new CatalogFilters(skills, formats, levels, types, topics);
    }

    private Optional<Long> userId(UUID publicId) {
        return jdbc.sql("select id from app_user where public_id = ?").param(publicId).query(Long.class).optional();
    }
}
