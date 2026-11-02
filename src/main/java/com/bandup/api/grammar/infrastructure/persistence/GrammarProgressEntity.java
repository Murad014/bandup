package com.bandup.api.grammar.infrastructure.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/** {@code grammar_progress} with its {@code grammar_finished_lesson} rows, keyed by lesson id. */
@Entity
@Table(name = "grammar_progress")
class GrammarProgressEntity {

    /** {@code app_user.id}. */
    @Id
    @Column(name = "user_id")
    Long userId;

    String level;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grammar_finished_lesson", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "lesson_id")
    Map<Long, FinishedLessonRow> finished = new HashMap<>();
}
