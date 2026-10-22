package com.bandup.api.practice.infrastructure.persistence;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One {@code attempt_answer} row. {@code given_value} and
 * {@code expected_value} are {@code jsonb}: the JSON text is written by
 * {@link AttemptJson} ({@code "FALSE"}, {@code ["A","C"]}).
 */
@Embeddable
class AttemptAnswerRow {

    @Column(name = "question_id", nullable = false)
    Long questionId;

    @Column(name = "question_number", nullable = false)
    int questionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "given_value", nullable = false)
    String givenValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_value", nullable = false)
    String expectedValue;

    @Column(name = "points_awarded", nullable = false)
    int pointsAwarded;

    @Column(name = "points_possible", nullable = false)
    int pointsPossible;
}
