package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code grammar_review_card}. */
@Entity
@Table(name = "grammar_review_card")
class ReviewCardEntity {

    @Id
    UUID id;

    /** {@code app_user.id}. */
    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "lesson_id", nullable = false)
    Long lessonId;

    @Column(name = "item_id", nullable = false)
    String itemId;

    @Column(nullable = false)
    short box;

    @Column(name = "due_on", nullable = false)
    LocalDate dueOn;

    @Column(name = "last_answered_on")
    LocalDate lastAnsweredOn;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
