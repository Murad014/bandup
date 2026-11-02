package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code grammar_run}; {@code mastered} is a JSON array of item ids. */
@Entity
@Table(name = "grammar_run")
class LessonRunEntity {

    @Id
    UUID id;

    @Column(name = "lesson_id", nullable = false)
    Long lessonId;

    @Column(name = "lesson_revision", nullable = false)
    int lessonRevision;

    /** {@code app_user.id}; null for a guest. */
    @Column(name = "user_id")
    Long userId;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String mastered;

    @Column(nullable = false)
    int retries;

    @Column(nullable = false)
    int answered;
}
