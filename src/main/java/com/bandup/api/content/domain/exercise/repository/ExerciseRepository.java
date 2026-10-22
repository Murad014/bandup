package com.bandup.api.content.domain.exercise.repository;

import java.util.Optional;

import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.GroupId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Slug;

/**
 * Loads and stores exercises whole. Also hands out ids for new exercises,
 * groups and questions before they enter the aggregate, so a question keeps
 * its id for life (design §8.2). Implemented in infrastructure.
 */
public interface ExerciseRepository {

    Optional<Exercise> findBySlug(Slug slug);

    boolean existsBySlug(Slug slug);

    ExerciseId nextExerciseId();

    GroupId nextGroupId();

    QuestionId nextQuestionId();

    /**
     * Inserts or updates the whole exercise. Fails with a conflict if the
     * stored version isn't the one the exercise was loaded with.
     *
     * @return the new stored version (0 for a new exercise, then +1 per save)
     */
    long save(Exercise exercise);

    void delete(Exercise exercise);
}
