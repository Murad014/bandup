package com.bandup.api.practice.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * {@code attempt} with its {@code attempt_answer} rows. Attempts are only
 * inserted, never updated. {@code started_at} isn't tracked yet (null) and
 * the generated {@code is_correct} column isn't mapped.
 */
@Entity
@Table(name = "attempt")
class AttemptEntity {

    @Id
    UUID id;

    /** {@code app_user.id}; null for an anonymous attempt. */
    @Column(name = "user_id")
    Long userId;

    @Column(name = "exercise_id", nullable = false)
    Long exerciseId;

    @Column(name = "exercise_revision", nullable = false)
    int exerciseRevision;

    @Column(name = "support_language", nullable = false)
    String supportLanguage;

    @Column(name = "submitted_at", nullable = false)
    Instant submittedAt;

    @Column(name = "elapsed_seconds", nullable = false)
    int elapsedSeconds;

    @Column(nullable = false)
    int score;

    @Column(nullable = false)
    int total;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attempt_answer", joinColumns = @JoinColumn(name = "attempt_id"))
    @OrderBy("questionNumber")
    List<AttemptAnswerRow> answers = new ArrayList<>();
}
