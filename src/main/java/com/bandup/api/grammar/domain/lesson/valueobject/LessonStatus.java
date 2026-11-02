package com.bandup.api.grammar.domain.lesson.valueobject;

import com.bandup.api.shared.domain.ddd.ValueObject;

/** DRAFT (admins only), PUBLISHED (in the course) or ARCHIVED (final; kept because learners used it). */
@ValueObject
public enum LessonStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
