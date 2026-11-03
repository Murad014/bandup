package com.bandup.api.vocabulary.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code vocab_session}; words, tasks (with keys), queue and mastered tasks as one JSON document. */
@Entity
@Table(name = "vocab_session")
class WordSessionEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(nullable = false)
    String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String content;

    @Column(nullable = false)
    int retries;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;
}
