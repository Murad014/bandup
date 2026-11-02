package com.bandup.api.grammar.infrastructure.readmodel;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.port.LearnerCourses;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;
import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;
import com.bandup.api.grammar.domain.review.valueobject.ReviewCardId;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LearnerCourses} in SQL. It reads only the lesson columns and the
 * {@code title}, {@code topic} and {@code minutes} of the body, never its
 * items, so nothing here can carry an answer key.
 */
@Component
@RequiredArgsConstructor
class JdbcLearnerCourses implements LearnerCourses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The columns of a {@link LessonEntry}. */
    private static final String ENTRY = """
            l.id, l.slug, l.position, l.content -> 'title' as title, l.topic_key,
            l.content -> 'topic' -> 'label' as topic_label, (l.content ->> 'minutes')::int as minutes""";

    private static final String FINISHED = """
            select f.lesson_id from grammar_finished_lesson f join app_user u on u.id = f.user_id
            where u.public_id = cast(:learner as uuid)""";

    private final JdbcClient jdbc;

    @Override
    public Map<String, Integer> lessonCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GrammarLevel level : GrammarLevel.values()) {
            counts.put(level.name(), 0);
        }
        jdbc.sql("select level, count(*) as n from grammar_lesson where status = 'PUBLISHED' group by level")
                .query(rs -> {
                    counts.put(rs.getString("level"), rs.getInt("n"));
                });
        return counts;
    }

    @Override
    public CourseFacts facts(String level, Optional<LearnerId> learner) {
        UUID me = learner.map(LearnerId::value).orElse(null);
        int available = jdbc.sql("select count(*) from grammar_lesson where status = 'PUBLISHED' and level = :level")
                .param("level", level).query(Integer.class).single();
        int finished = me == null ? 0 : jdbc.sql("""
                select count(*) from grammar_lesson l
                where l.status = 'PUBLISHED' and l.level = :level and l.id in (%s)""".formatted(FINISHED))
                .param("level", level).param("learner", me).query(Integer.class).single();
        Optional<LessonEntry> first = jdbc.sql("""
                select %s from grammar_lesson l where l.status = 'PUBLISHED' and l.level = :level
                order by l.position limit 1""".formatted(ENTRY))
                .param("level", level).query(this::entry).optional();
        Optional<LessonEntry> firstUnfinished = me == null ? first : jdbc.sql("""
                select %s from grammar_lesson l where l.status = 'PUBLISHED' and l.level = :level
                  and l.id not in (%s)
                order by l.position limit 1""".formatted(ENTRY, FINISHED))
                .param("level", level).param("learner", me).query(this::entry).optional();
        return new CourseFacts(available, finished, first, firstUnfinished);
    }

    @Override
    public CoursePage page(String level, String topic, int offset, int limit) {
        String where = "l.status = 'PUBLISHED' and l.level = :level"
                + " and (cast(:topic as varchar) is null or l.topic_key = :topic)";
        int total = jdbc.sql("select count(*) from grammar_lesson l where " + where)
                .param("level", level).param("topic", topic).query(Integer.class).single();
        List<LessonEntry> lessons = jdbc.sql("select " + ENTRY + " from grammar_lesson l where " + where
                + " order by l.position limit :limit offset :offset")
                .param("level", level).param("topic", topic).param("limit", limit).param("offset", offset)
                .query(this::entry).list();
        return new CoursePage(lessons, total);
    }

    @Override
    public List<TopicEntry> topics(String level) {
        return jdbc.sql("""
                select key, label from (
                    select distinct on (topic_key) topic_key as key, content -> 'topic' -> 'label' as label, position
                    from grammar_lesson where status = 'PUBLISHED' and level = :level
                    order by topic_key, position) t
                order by position""")
                .param("level", level)
                .query((rs, n) -> new TopicEntry(rs.getString("key"), texts(rs.getString("label")))).list();
    }

    @Override
    public List<DueCard> dueCards(LearnerId learner, LocalDate today, int limit) {
        return jdbc.sql("""
                select c.id, c.lesson_id, c.item_id
                from grammar_review_card c
                join grammar_lesson l on l.id = c.lesson_id and l.status = 'PUBLISHED'
                join app_user u on u.id = c.user_id
                where u.public_id = :learner and c.due_on <= :today
                order by c.due_on, c.created_at, c.id
                limit :limit""")
                .param("learner", learner.value()).param("today", Date.valueOf(today)).param("limit", limit)
                .query((rs, n) -> new DueCard(new ReviewCardId(rs.getObject("id", UUID.class)),
                        new LessonId(rs.getLong("lesson_id")), new ItemId(rs.getString("item_id"))))
                .list();
    }

    @Override
    public ReviewSummary reviewSummary(LearnerId learner, LocalDate today) {
        return jdbc.sql("""
                select count(*) filter (where c.due_on <= :today) as due,
                       min(c.due_on) filter (where c.due_on > :today) as next_due
                from grammar_review_card c
                join grammar_lesson l on l.id = c.lesson_id and l.status = 'PUBLISHED'
                join app_user u on u.id = c.user_id
                where u.public_id = :learner""")
                .param("learner", learner.value()).param("today", Date.valueOf(today))
                .query((rs, n) -> {
                    int due = rs.getInt("due");
                    Date next = rs.getDate("next_due");
                    return new ReviewSummary(due, due > 0 || next == null ? null : next.toLocalDate());
                })
                .single();
    }

    private LessonEntry entry(ResultSet rs, int row) throws SQLException {
        return new LessonEntry(new LessonId(rs.getLong("id")), rs.getString("slug"), rs.getInt("position"),
                texts(rs.getString("title")), rs.getString("topic_key"), texts(rs.getString("topic_label")),
                rs.getInt("minutes"));
    }

    /** A stored text object ({@code {"en": …}}) as a map. */
    private static Map<String, String> texts(String json) {
        Map<String, String> texts = new LinkedHashMap<>();
        if (json != null) {
            JSON.readTree(json).properties().forEach(e -> texts.put(e.getKey(), e.getValue().asString()));
        }
        return texts;
    }
}
