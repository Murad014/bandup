package com.bandup.api.content.domain.exercise.valueobject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Everything the admin editor saves at once (slug, skill and target language
 * are fixed when the exercise is created).
 *
 * @param passageHtml the sanitized passage, or null when the exercise has none
 * @param markup      the keys found in {@code passageHtml} ({@link MarkupKeys#none()} without one)
 * @param glossNotes  lemma and meanings per gloss key; notes for keys no longer
 *                    marked in the HTML are dropped
 * @param groups      in display order; new groups/questions carry fresh ids from the repository
 * @param cover       the cover image shown in the catalog and above the passage, or null
 */
@ValueObject
public record ExerciseContent(String title, Format format, Level level, Timer timer, Placement placement,
        Set<TopicId> topics, SanitizedHtml passageHtml, MarkupKeys markup, Map<GlossKey, GlossNote> glossNotes,
        List<QuestionGroup> groups, MediaId cover) {

    /** Without a cover image. */
    public ExerciseContent(String title, Format format, Level level, Timer timer, Placement placement,
            Set<TopicId> topics, SanitizedHtml passageHtml, MarkupKeys markup, Map<GlossKey, GlossNote> glossNotes,
            List<QuestionGroup> groups) {
        this(title, format, level, timer, placement, topics, passageHtml, markup, glossNotes, groups, null);
    }

    public ExerciseContent {
        title = title == null ? "" : title.strip();
        Objects.requireNonNull(format, "format");
        topics = Set.copyOf(topics);
        markup = markup == null ? MarkupKeys.none() : markup;
        glossNotes = Map.copyOf(glossNotes);
        groups = List.copyOf(groups);
    }

    public Optional<Level> levelIfAny() {
        return Optional.ofNullable(level);
    }
}
