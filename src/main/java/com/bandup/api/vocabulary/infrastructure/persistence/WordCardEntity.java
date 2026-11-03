package com.bandup.api.vocabulary.infrastructure.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code vocab_word}; its forms are rows of {@code vocab_word_form}, kept with plain SQL. */
@Entity
@Table(name = "vocab_word")
class WordCardEntity {

    @Id
    Long id;

    String lemma;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    String source;

    @Column(nullable = false)
    int revision;

    @Column(nullable = false)
    int attempts;

    @Column(name = "last_error")
    String lastError;

    @JdbcTypeCode(SqlTypes.JSON)
    String content;

    @Column(nullable = false)
    long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
