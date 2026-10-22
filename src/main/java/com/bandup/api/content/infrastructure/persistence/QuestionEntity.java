package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code question}; its id comes from the domain (kept across edits, attempts point at it). */
@Entity
@Table(name = "question")
class QuestionEntity {


    @Id
    Long id;

    @Column(name = "exercise_id", nullable = false)
    Long exerciseId;

    @Column(name = "group_id", nullable = false)
    Long groupId;

    @Column(nullable = false)
    int position;

    @Column(name = "stem_text")
    String stemText;

    @Column(name = "stem_pre")
    String stemPre;

    @Column(name = "stem_post")
    String stemPost;

    @Column(name = "evidence_id")
    Long evidenceId;
}
