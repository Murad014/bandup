package com.bandup.api.content.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code exercise}. {@code version} is a plain column: the adapter locks the row, checks it and raises it on every save. */
@Entity
@Table(name = "exercise")
class ExerciseEntity {


    @Id
    Long id;

    @Column(nullable = false)
    String slug;

    @Column(nullable = false)
    String skill;

    @Column(name = "target_language", nullable = false)
    String targetLanguage;

    @Column(name = "stage_id")
    Long stageId;

    Integer position;

    @Column(nullable = false)
    String title;

    @Column(nullable = false)
    String format;

    String level;

    @Column(name = "duration_seconds")
    Integer durationSeconds;

    @Column(name = "low_time_threshold_seconds")
    Integer lowTimeThresholdSeconds;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    int revision;

    @Column(nullable = false)
    long version;

    @Column(name = "published_at")
    Instant publishedAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
