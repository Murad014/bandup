package com.bandup.api.grammar.domain.progress.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** The level of a learner's course; the same codes as a lesson's level (A2, B1, B2, C1). */
@ValueObject
public enum CourseLevel {
    A2,
    B1,
    B2,
    C1
}
