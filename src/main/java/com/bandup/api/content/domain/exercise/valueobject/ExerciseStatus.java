package com.bandup.api.content.domain.exercise.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** Lifecycle: only PUBLISHED exercises are shown to learners; ARCHIVED is final. */
@ValueObject
public enum ExerciseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
