package com.bandup.api.content.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.content.application.port.AttemptsLookup;
import com.bandup.api.content.application.port.ExerciseSummaries;
import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.repository.ExerciseRepository;
import com.bandup.api.content.domain.exercise.service.PublishPolicy;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseContent;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.application.InvalidRequestException;
import com.bandup.api.shared.application.LanguageCatalog;
import com.bandup.api.shared.application.NotFoundException;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.numbering.NumberRange;

/**
 * The seven admin use cases (backend requirement 4, design §8.3): list, get
 * for editing, create, update, publish, unpublish, delete. Each runs in one
 * transaction; every write goes through the {@link Exercise} aggregate and
 * {@link ExerciseRepository#save}, which rejects stale versions (409).
 */
@Service
@RequiredArgsConstructor
public class ExerciseAdmin {

    private static final PublishPolicy POLICY = new PublishPolicy();

    private final ExerciseRepository exercises;
    private final ExerciseContentBuilder contentBuilder;
    private final ExerciseSummaries summaries;
    private final AttemptsLookup attempts;
    private final LanguageCatalog languages;
    private final Clock clock;
    private final ImageLibrary images;

    /** ListExercises: every exercise, newest change first. */
    @Transactional(readOnly = true)
    public List<ExerciseSummary> list() {
        return summaries.all();
    }

    /** GetExerciseForEditing: the full object, answers included. */
    @Transactional(readOnly = true)
    public AdminExerciseView get(String slug) {
        Exercise exercise = load(slug);
        return view(exercise, exercise.version());
    }

    /** CreateExercise: a new DRAFT; only the draft rules apply. */
    @Transactional
    public AdminExerciseView create(ExerciseInput in) {
        Slug slug = newSlug(in.id());
        Skill skill = in.skill() == null ? Skill.READING : parseSkill(in.skill());
        LanguageCode target = targetLanguage(in.targetLanguage());
        ExerciseContent content = contentBuilder.build(in, null);
        Exercise exercise = Exercise.draft(exercises.nextExerciseId(), slug, skill, target, content);
        return view(exercise, exercises.save(exercise));
    }

    /**
     * UpdateExercise: replaces the content. A PUBLISHED exercise must stay
     * publishable and gets a new revision.
     *
     * @throws ConflictException if {@code in.version} isn't the stored version
     */
    @Transactional
    public AdminExerciseView update(String slug, ExerciseInput in) {
        Exercise exercise = load(slug);
        if (in.version() == null) {
            throw new InvalidRequestException("The version is required.",
                    List.of(new Violation("version", "Send the version you loaded.")));
        }
        if (in.version() != exercise.version()) {
            throw new ConflictException("This exercise was changed by someone else. Reload it and try again.");
        }
        ExerciseContent content = contentBuilder.build(in, exercise);
        refuseRemovingAnsweredQuestions(exercise, content);
        exercise.editContent(content, POLICY);
        return view(exercise, exercises.save(exercise));
    }

    /** PublishExercise: shows it to learners if it passes every publish rule (422 otherwise). */
    @Transactional
    public AdminExerciseView publish(String slug) {
        return change(slug, e -> e.publish(POLICY, clock.instant()));
    }

    /** UnpublishExercise: hides it from learners (back to DRAFT). */
    @Transactional
    public AdminExerciseView unpublish(String slug) {
        return change(slug, Exercise::unpublish);
    }

    /**
     * DeleteExercise: removed for good, or archived when learners have
     * attempts on it (their history must stay).
     *
     * @return true if it was archived instead of removed
     */
    @Transactional
    public boolean delete(String slug) {
        Exercise exercise = load(slug);
        if (attempts.hasAttempts(exercise.id())) {
            exercise.archive();
            exercises.save(exercise);
            return true;
        }
        exercises.delete(exercise);
        return false;
    }

    private AdminExerciseView view(Exercise exercise, long version) {
        String coverUrl = exercise.cover().flatMap(cover -> images.urlOf(cover.value())).orElse(null);
        return ExerciseViews.of(exercise, version, coverUrl);
    }

    private AdminExerciseView change(String slug, Consumer<Exercise> change) {
        Exercise exercise = load(slug);
        change.accept(exercise);
        return view(exercise, exercises.save(exercise));
    }

    private Exercise load(String slug) {
        Slug parsed;
        try {
            parsed = new Slug(slug);
        } catch (DomainException e) {
            throw new NotFoundException("Exercise not found.");
        }
        return exercises.findBySlug(parsed).orElseThrow(() -> new NotFoundException("Exercise not found."));
    }

    private Slug newSlug(String value) {
        Slug slug;
        try {
            slug = new Slug(value);
        } catch (DomainException e) {
            throw new DomainException(new Violation("id", e.violations().getFirst().message()));
        }
        if (exercises.existsBySlug(slug)) {
            throw DomainException.of("id", "Another exercise already uses '" + slug + "'.");
        }
        return slug;
    }

    private static Skill parseSkill(String value) {
        try {
            return Skill.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw DomainException.of("skill", "Unknown skill '" + value + "'.");
        }
    }

    private LanguageCode targetLanguage(String value) {
        if (value == null) {
            return LanguageCode.ENGLISH;
        }
        try {
            LanguageCode code = LanguageCode.of(value);
            return languages.fallbacks().storedSpelling(code)
                    .orElseThrow(() -> DomainException.of("targetLanguage", "Unknown language '" + value + "'."));
        } catch (DomainException e) {
            throw DomainException.of("targetLanguage", "Unknown language '" + value + "'.");
        }
    }

    /** Recorded attempt answers point at their question (RESTRICT): such a question must stay. */
    private void refuseRemovingAnsweredQuestions(Exercise exercise, ExerciseContent content) {
        Set<QuestionId> kept = content.groups().stream().flatMap(g -> g.questions().stream()).map(q -> q.id())
                .collect(Collectors.toSet());
        Set<QuestionId> answered = attempts.answeredQuestions(exercise.id());
        Map<QuestionId, NumberRange> numbers = exercise.numbering();
        List<Violation> violations = numbers.keySet().stream()
                .filter(id -> !kept.contains(id) && answered.contains(id))
                .map(id -> new Violation("questionGroups", "Q" + numbers.get(id) + " can't be removed: learners have "
                        + "answered it. Keep it, or archive this exercise and make a new one."))
                .toList();
        if (!violations.isEmpty()) {
            throw new DomainException(violations);
        }
    }
}
