package com.bandup.api.content.application;

import java.time.Instant;

/**
 * One row of the admin list.
 *
 * @param id         the slug
 * @param stageTitle the stage's English title, if the exercise is in a stage
 */
public record ExerciseSummary(String id, String title, String status, int revision, String stageTitle,
        Integer position, int questionCount, Instant updatedAt) {
}
