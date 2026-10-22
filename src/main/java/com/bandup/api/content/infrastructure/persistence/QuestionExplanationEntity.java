package com.bandup.api.content.infrastructure.persistence;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** {@code question_explanation}: why the answer is right, in one language. */
@Entity
@Table(name = "question_explanation")
@IdClass(QuestionExplanationEntity.Key.class)
class QuestionExplanationEntity {

    /** Composite primary key. */
    record Key(Long questionId, String languageCode) implements Serializable {
    }


    @Id
    @Column(name = "question_id")
    Long questionId;

    @Id
    @Column(name = "language_code")
    String languageCode;

    @Column(name = "explanation_html", nullable = false)
    String explanationHtml;

    @Column(nullable = false)
    String source;

    @Column(nullable = false)
    boolean reviewed;
}
