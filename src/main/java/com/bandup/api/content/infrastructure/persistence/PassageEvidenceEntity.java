package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code passage_evidence}: one evidence span, matched by key on save (questions point at its id). */
@Entity
@Table(name = "passage_evidence")
class PassageEvidenceEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "exercise_id", nullable = false)
    Long exerciseId;

    @Column(name = "evidence_key", nullable = false)
    String evidenceKey;
}
