package com.bandup.api.content.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code question_option}: owned by a group (matching bank) or by a question (MCQ / MCMA). */
@Entity
@Table(name = "question_option")
class QuestionOptionEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "group_id")
    Long groupId;

    @Column(name = "question_id")
    Long questionId;

    @Column(name = "option_key", nullable = false)
    String optionKey;

    @Column(nullable = false)
    String label;

    @Column(nullable = false)
    int position;
}
