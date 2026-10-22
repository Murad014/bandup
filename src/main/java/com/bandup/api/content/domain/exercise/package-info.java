/**
 * The <b>Exercise</b> aggregate: one exercise (e.g. a mini reading) with its
 * passage and questions, edited and saved as a whole by the admin editor.
 * Split by building block:
 *
 * <ul>
 *   <li>{@code aggregate}: {@code Exercise} (root: drafting, editing, markup sync, publish
 *       lifecycle, revision, numbering)</li>
 *   <li>{@code entity}: {@code Passage}, {@code Gloss}, {@code QuestionGroup},
 *       {@code Question} (immutable; only the root swaps them)</li>
 *   <li>{@code valueobject}: ids, metadata, keys, stems, answers, help text</li>
 *   <li>{@code service}: {@code PublishPolicy} (backend requirement 4.2)</li>
 *   <li>{@code repository}: {@code ExerciseRepository} (port; also hands out new ids)</li>
 * </ul>
 *
 * <p>Stages, topics, media and question types are reference data, referenced
 * only by id or code.
 */
package com.bandup.api.content.domain.exercise;
