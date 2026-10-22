/**
 * Content — persistence adapter for the Exercise aggregate: one plain JPA
 * entity per table (no JPA associations) and {@code JpaExerciseRepository},
 * which loads and saves the whole aggregate in an explicit order.
 */
package com.bandup.api.content.infrastructure.persistence;
