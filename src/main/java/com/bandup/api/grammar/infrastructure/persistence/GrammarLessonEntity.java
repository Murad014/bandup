package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code grammar_lesson}: queried fields as columns, the body as JSON ({@link LessonJson}). */
@Entity
@Table(name = "grammar_lesson")
class GrammarLessonEntity {

    @Id
    Long id;

    @Column(nullable = false)
    String slug;

    @Column(nullable = false)
    String level;

    @Column(name = "topic_key", nullable = false)
    String topicKey;

    @Column(nullable = false)
    String title;

    Integer position;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    int revision;

    @Column(nullable = false)
    long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String content;

    @Column(name = "published_at")
    Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
