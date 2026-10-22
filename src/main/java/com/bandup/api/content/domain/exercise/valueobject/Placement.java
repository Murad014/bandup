package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.shared.domain.error.DomainException;

/** Where an exercise sits in a course: its stage and position there (optional grouping). */
@ValueObject
public record Placement(StageId stage, int position) {

    public Placement {
        Objects.requireNonNull(stage, "stage");
        if (position < 1) {
            throw DomainException.of("position", "The position in the stage must be 1 or more.");
        }
    }
}
