package com.bandup.api.content.infrastructure.persistence;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** {@code passage_gloss_translation}: a gloss meaning in one language. */
@Entity
@Table(name = "passage_gloss_translation")
@IdClass(PassageGlossTranslationEntity.Key.class)
class PassageGlossTranslationEntity {

    /** Composite primary key. */
    record Key(Long glossId, String languageCode) implements Serializable {
    }


    @Id
    @Column(name = "gloss_id")
    Long glossId;

    @Id
    @Column(name = "language_code")
    String languageCode;

    @Column(nullable = false)
    String meaning;

    @Column(nullable = false)
    String source;

    @Column(nullable = false)
    boolean reviewed;
}
