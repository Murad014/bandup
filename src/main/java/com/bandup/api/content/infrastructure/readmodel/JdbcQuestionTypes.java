package com.bandup.api.content.infrastructure.readmodel;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.QuestionTypes;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;

/** {@link QuestionTypes} from the {@code question_type} table (enabled types only). */
@Component
@RequiredArgsConstructor
class JdbcQuestionTypes implements QuestionTypes {

    private final JdbcClient jdbc;

    @Override
    public Map<String, QuestionFamily> all() {
        Map<String, QuestionFamily> types = new HashMap<>();
        jdbc.sql("select code, family from question_type where enabled").query(rs -> {
            types.put(rs.getString("code"), QuestionFamily.ofCode(rs.getString("family")));
        });
        return types;
    }
}
