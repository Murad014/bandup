package com.bandup.api.vocabulary.infrastructure.persistence;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;

/**
 * Maps a learner's public id to {@code app_user.id} and back with plain SQL,
 * so Vocabulary never uses Identity's classes.
 */
@Component("vocabularyLearners")
@RequiredArgsConstructor
public class Learners {

    private final JdbcClient jdbc;

    public Long userId(LearnerId learner) {
        return jdbc.sql("select id from app_user where public_id = ?").param(learner.value()).query(Long.class).optional()
                .orElseThrow(() -> new IllegalStateException("No user with public id " + learner));
    }

    public LearnerId learnerId(Long userId) {
        return new LearnerId(jdbc.sql("select public_id from app_user where id = ?").param(userId).query(UUID.class)
                .single());
    }
}
