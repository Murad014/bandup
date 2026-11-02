package com.bandup.api.grammar.infrastructure.readmodel;

import java.sql.Timestamp;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.LessonSummary;
import com.bandup.api.grammar.application.port.LessonSummaries;

/** {@link LessonSummaries} in SQL. */
@Component
@RequiredArgsConstructor
class JdbcLessonSummaries implements LessonSummaries {

    private final JdbcClient jdbc;

    @Override
    public List<LessonSummary> list(String level, String status) {
        return jdbc.sql("""
                select slug, level, topic_key, title, status, position, revision,
                       jsonb_array_length(content -> 'check') as check_count, updated_at
                from grammar_lesson
                where (cast(:level as varchar) is null or level = :level)
                  and (cast(:status as varchar) is null or status = :status)
                order by updated_at desc, slug
                """)
                .param("level", level).param("status", status)
                .query((rs, n) -> new LessonSummary(rs.getString("slug"), rs.getString("level"),
                        rs.getString("topic_key"), rs.getString("title"), rs.getString("status"),
                        (Integer) rs.getObject("position"), rs.getInt("revision"), rs.getInt("check_count"),
                        rs.getObject("updated_at", Timestamp.class).toInstant()))
                .list();
    }
}
