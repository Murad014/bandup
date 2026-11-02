package com.bandup.api.grammar.infrastructure.readmodel;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.port.LessonUsage;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonId;

/** {@link LessonUsage} in SQL over the learner tables. */
@Component
@RequiredArgsConstructor
class JdbcLessonUsage implements LessonUsage {

    private final JdbcClient jdbc;

    @Override
    public boolean isUsed(LessonId lesson) {
        return jdbc.sql("""
                select exists (select 1 from grammar_run where lesson_id = :id)
                    or exists (select 1 from grammar_finished_lesson where lesson_id = :id)
                    or exists (select 1 from grammar_review_card where lesson_id = :id)
                """).param("id", lesson.value()).query(Boolean.class).single();
    }
}
