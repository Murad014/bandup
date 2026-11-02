package com.bandup.api.grammar.application;

import java.time.Instant;

/** One row of the admin lesson list. {@code id} is the slug. */
public record LessonSummary(String id, String level, String topic, String title, String status, Integer position,
        int revision, int checkCount, Instant updatedAt) {
}
