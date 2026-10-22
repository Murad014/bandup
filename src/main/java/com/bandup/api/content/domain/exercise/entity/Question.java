package com.bandup.api.content.domain.exercise.entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
public final class Question {

    private final QuestionId id;
    private final Stem stem;
    private final EvidenceKey evidence;
    private final List<Option> options;
    private final AnswerKey answer;
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

    public QuestionId id() {
        return id;
    }

    public Stem stem() {
        return stem;
    }

    /** The passage span that proves the answer. */
    public Optional<EvidenceKey> evidence() {
        return Optional.ofNullable(evidence);
    }

    public List<Option> options() {
        return options;
    }

    public AnswerKey answer() {
        return answer;
    }

    /** Explanation per help language. */
    public Translations<Explanation> explanations() {
        return explanations;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Question q && id.equals(q.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Question[" + id.value() + "]";
    }
}
