package com.bandup.api.vocabulary.infrastructure.readmodel;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.AiUsage;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;

/** {@link AiUsage} in {@code vocab_ai_usage}, one row per learner and day. */
@Component
@RequiredArgsConstructor
class JdbcAiUsage implements AiUsage {

    private final JdbcClient jdbc;

    @Override
    public int count(LearnerId learner, LocalDate day) {
        return jdbc.sql("""
                select coalesce((select u.count from vocab_ai_usage u join app_user a on a.id = u.user_id
                                 where a.public_id = ? and u.day = ?), 0)""")
                .params(learner.value(), day).query(Integer.class).single();
    }

    @Override
    public int record(LearnerId learner, LocalDate day) {
        return jdbc.sql("""
                insert into vocab_ai_usage (user_id, day, count)
                select id, ?, 1 from app_user where public_id = ?
                on conflict (user_id, day) do update set count = vocab_ai_usage.count + 1
                returning count""")
                .params(day, learner.value()).query(Integer.class).single();
    }
}
