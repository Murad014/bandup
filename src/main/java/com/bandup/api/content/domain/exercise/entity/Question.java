package com.bandup.api.content.domain.exercise.entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.Explanation;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.shared.domain.ddd.Entity;
import com.bandup.api.shared.domain.language.Translations;

/**
 * One question, identified by its id (kept across edits). Immutable; the
 * {@code Exercise} replaces it as a whole.
 *
 * @param options its own options (MCQ / MCMA only); matching uses the group's bank
 */
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Question {

    @EqualsAndHashCode.Include
    @Getter
    private final QuestionId id;
    @Getter
    private final Stem stem;
    private final EvidenceKey evidence;
    @Getter
    private final List<Option> options;
    @Getter
    private final AnswerKey answer;
    /** Explanation per help language. */
    @Getter
    private final Translations<Explanation> explanations;

    public Question(QuestionId id, Stem stem, EvidenceKey evidence, List<Option> options, AnswerKey answer,
            Translations<Explanation> explanations) {
        this.id = Objects.requireNonNull(id, "id");
        this.stem = Objects.requireNonNull(stem, "stem");
        this.evidence = evidence;
        this.options = List.copyOf(options);
        this.answer = Objects.requireNonNull(answer, "answer");
        this.explanations = Objects.requireNonNull(explanations, "explanations");
    }

    /** The passage span that proves the answer. */
    public Optional<EvidenceKey> evidence() {
        return Optional.ofNullable(evidence);
    }

    @Override
    public String toString() {
        return "Question[" + id.value() + "]";
    }
}
