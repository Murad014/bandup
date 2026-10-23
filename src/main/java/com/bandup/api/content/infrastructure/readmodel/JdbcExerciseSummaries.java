package com.bandup.api.content.infrastructure.readmodel;

import java.sql.Timestamp;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.ExerciseSummary;
import com.bandup.api.content.application.port.ExerciseSummaries;

/** {@link ExerciseSummaries}: one query over {@code exercise} with its stage title and question count. */
@Component
@RequiredArgsConstructor
class JdbcExerciseSummaries implements ExerciseSummaries {

    private static final String SQL = """
            select x.slug, x.title, x.status, x.revision, st.title as stage_title, x.position, x.updated_at,
                   (select count(*) from question q where q.exercise_id = x.id) as question_count
            from exercise x
            left join stage_translation st on st.stage_id = x.stage_id and st.language_code = 'en'
            order by x.updated_at desc, x.slug""";

    private final JdbcClient jdbc;

    @Override
    public List<ExerciseSummary> all() {
        return jdbc.sql(SQL).query((rs, i) -> new ExerciseSummary(rs.getString("slug"), rs.getString("title"),
                rs.getString("status"), rs.getInt("revision"), rs.getString("stage_title"),
                rs.getObject("position", Integer.class), rs.getInt("question_count"),
                rs.getObject("updated_at", Timestamp.class).toInstant())).list();
    }
}
