/**
 * The <b>Attempt</b> aggregate: one graded submission of an exercise. Split by
 * building block:
 *
 * <ul>
 *   <li>{@code aggregate}: {@code Attempt} (root; immutable, history is never rewritten)</li>
 *   <li>{@code valueobject}: ids ({@code AttemptId}, {@code ExerciseId}, {@code QuestionId},
 *       {@code LearnerId}), the submission ({@code AnswerSheet}, {@code GivenAnswer}), the result
 *       ({@code GradedAnswer}, {@code Score}) and Practice's own read-only view of the exercise
 *       ({@code GradableExercise}, {@code GradableQuestion}, {@code GradingRule},
 *       {@code QuestionFamily}, {@code WordLimit})</li>
 *   <li>{@code service}: {@code Grader} (the IELTS scoring rules)</li>
 *   <li>{@code exception}: {@code InvalidSubmissionException}</li>
 *   <li>{@code repository}: {@code AttemptRepository} (port)</li>
 * </ul>
 *
 * <p>Never imports Content or Identity classes: exercises and users are
 * referenced only by Practice's own id value objects.
 */
package com.bandup.api.practice.domain.attempt;
