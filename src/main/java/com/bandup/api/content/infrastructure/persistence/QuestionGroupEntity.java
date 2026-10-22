package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code question_group}; its id comes from the domain (kept across edits). */
@Entity
@Table(name = "question_group")
class QuestionGroupEntity {


    @Id
    Long id;

    @Column(name = "exercise_id", nullable = false)
    Long exerciseId;

    @Column(nullable = false)
    int position;

    @Column(name = "type_code", nullable = false)
    String typeCode;

    @Column(name = "instruction_html", nullable = false)
    String instructionHtml;

    @Column(name = "choose_count")
    Integer chooseCount;

    @Column(name = "max_words")
    Integer maxWords;

    @Column(name = "numbers_allowed", nullable = false)
    boolean numbersAllowed;

    @Column(name = "options_reusable", nullable = false)
    boolean optionsReusable;

    @Column(name = "media_id")
    Long mediaId;
}
