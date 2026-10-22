package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code question_answer}: one accepted value / required key, in order. */
@Entity
@Table(name = "question_answer")
class QuestionAnswerEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "question_id", nullable = false)
    Long questionId;

    @Column(nullable = false)
    String value;

    @Column(nullable = false)
    int position;
}
