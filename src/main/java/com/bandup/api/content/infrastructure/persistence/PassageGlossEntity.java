package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code passage_gloss}: one glossed word, matched by key on save. */
@Entity
@Table(name = "passage_gloss")
class PassageGlossEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "exercise_id", nullable = false)
    Long exerciseId;

    @Column(name = "gloss_key", nullable = false)
    String glossKey;

    @Column(nullable = false)
    String surface;

    String lemma;
}
