package com.bandup.api.grammar.infrastructure.persistence;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.domain.progress.valueobject.LearnerId;

/**
 * Maps a learner's public id to {@code app_user.id} and back with plain SQL,
 * so Grammar never uses Identity's classes.
 */
@Component
@RequiredArgsConstructor
class Learners {

    private final JdbcClient jdbc;

    Long userId(LearnerId learner) {
        return jdbc.sql("select id from app_user where public_id = ?").param(learner.value()).query(Long.class).optional()
                .orElseThrow(() -> new IllegalStateException("No user with public id " + learner));
    }

    LearnerId learnerId(Long userId) {
        return new LearnerId(jdbc.sql("select public_id from app_user where id = ?").param(userId).query(UUID.class)
                .single());
    }
}
