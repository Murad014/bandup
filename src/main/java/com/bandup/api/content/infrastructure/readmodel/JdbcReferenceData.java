package com.bandup.api.content.infrastructure.readmodel;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.ReferenceData;

/** {@link ReferenceData}: existence checks on {@code stage}, {@code topic} and {@code media}. */
@Component
@RequiredArgsConstructor
class JdbcReferenceData implements ReferenceData {

    private final JdbcClient jdbc;

    @Override
    public boolean stageExists(long id) {
        return exists("select count(*) from stage where id = ?", id);
    }

    @Override
    public boolean topicExists(long id) {
        return exists("select count(*) from topic where id = ?", id);
    }

    @Override
    public boolean mediaExists(long id) {
        return exists("select count(*) from media where id = ?", id);
    }

    private boolean exists(String sql, long id) {
        return jdbc.sql(sql).param(id).query(Long.class).single() > 0;
    }
}
