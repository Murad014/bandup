package com.bandup.api.vocabulary.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code vocab_saved_word}. */
@Entity
@Table(name = "vocab_saved_word")
class SavedWordEntity {

    @Id
    UUID id;

    /** {@code app_user.id}. */
    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "word_id", nullable = false)
    Long wordId;

    @Column(nullable = false)
    String state;

    Short box;

    @Column(name = "due_on")
    LocalDate dueOn;

    @Column(name = "last_answered_on")
    LocalDate lastAnsweredOn;

    @Column(name = "source_exercise_id")
    Long sourceExerciseId;

    @Column(name = "source_sentence")
    String sourceSentence;

    @Column(name = "added_on", nullable = false)
    LocalDate addedOn;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
