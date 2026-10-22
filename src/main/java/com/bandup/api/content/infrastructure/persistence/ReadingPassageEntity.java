package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code reading_passage}: the passage HTML of an exercise. */
@Entity
@Table(name = "reading_passage")
class ReadingPassageEntity {


    @Id
    @Column(name = "exercise_id")
    Long exerciseId;

    @Column(name = "body_html", nullable = false)
    String bodyHtml;

    @Column(name = "word_count", nullable = false)
    int wordCount;
}
