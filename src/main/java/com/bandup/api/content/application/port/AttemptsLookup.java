package com.bandup.api.content.application.port;

import java.util.Set;

import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;

/** Port: what learners have done with an exercise (Practice's data, read with SQL). */
public interface AttemptsLookup {

    boolean hasAttempts(ExerciseId exercise);

    /** Questions of the exercise that recorded attempt answers point at. */
    Set<QuestionId> answeredQuestions(ExerciseId exercise);
}
