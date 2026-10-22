package com.bandup.api.content.domain.exercise.entity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.bandup.api.content.domain.exercise.valueobject.GroupId;
import com.bandup.api.content.domain.exercise.valueobject.MediaId;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.QuestionType;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.domain.ddd.Entity;
import com.bandup.api.shared.domain.language.Translations;

/**
 * Questions of one type under one instruction ("Questions 1–3"), identified
 * by its id. Immutable; the {@code Exercise} replaces it as a whole and checks
 * that its rules fit its type.
 *
 * @param chooseCount     "Choose N" (MCMA only), else null
 * @param wordLimit       gap word limit, else null
 * @param optionsReusable matching: may a key answer several questions?
 * @param media           the picture of a DIAGRAM_LABEL group, else null
 * @param bank            the shared options of a matching group, else empty
 */
@Entity
public final class QuestionGroup {

    private final GroupId id;
    private final QuestionType type;
    private final SanitizedHtml instruction;
    private final Translations<SanitizedHtml> instructionTranslations;
    private final Integer chooseCount;
    private final WordLimit wordLimit;
    private final boolean optionsReusable;
    private final MediaId media;
    private final List<Option> bank;
    private final List<Question> questions;

    public QuestionGroup(GroupId id, QuestionType type, SanitizedHtml instruction,
            Translations<SanitizedHtml> instructionTranslations, Integer chooseCount, WordLimit wordLimit,
            boolean optionsReusable, MediaId media, List<Option> bank, List<Question> questions) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.instruction = Objects.requireNonNull(instruction, "instruction");
        this.instructionTranslations = Objects.requireNonNull(instructionTranslations, "instructionTranslations");
        this.chooseCount = chooseCount;
        this.wordLimit = wordLimit;
        this.optionsReusable = optionsReusable;
        this.media = media;
        this.bank = List.copyOf(bank);
        this.questions = List.copyOf(questions);
    }

    public GroupId id() {
        return id;
    }

    public QuestionType type() {
        return type;
    }

    /** The official instruction in the target language. */
    public SanitizedHtml instruction() {
        return instruction;
    }

    /** Helper translations shown beside the official instruction. */
    public Translations<SanitizedHtml> instructionTranslations() {
        return instructionTranslations;
    }

    public Optional<Integer> chooseCount() {
        return Optional.ofNullable(chooseCount);
    }

    public Optional<WordLimit> wordLimit() {
        return Optional.ofNullable(wordLimit);
    }

    public boolean optionsReusable() {
        return optionsReusable;
    }

    public Optional<MediaId> media() {
        return Optional.ofNullable(media);
    }

    public List<Option> bank() {
        return bank;
    }

    public List<Question> questions() {
        return questions;
    }

    /** Points (and numbers) each question takes: N for "Choose N", else 1. */
    public int pointsPerQuestion() {
        return chooseCount != null && chooseCount > 0 ? chooseCount : 1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuestionGroup g && id.equals(g.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "QuestionGroup[" + id.value() + " " + type.code() + ", " + questions.size() + " questions]";
    }
}
