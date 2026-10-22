package com.bandup.api.content.infrastructure.persistence;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** {@code question_group_translation}: a helper translation of an instruction. */
@Entity
@Table(name = "question_group_translation")
@IdClass(QuestionGroupTranslationEntity.Key.class)
class QuestionGroupTranslationEntity {

    /** Composite primary key. */
    record Key(Long groupId, String languageCode) implements Serializable {
    }


    @Id
    @Column(name = "group_id")
    Long groupId;

    @Id
    @Column(name = "language_code")
    String languageCode;

    @Column(name = "instruction_html", nullable = false)
    String instructionHtml;
}
