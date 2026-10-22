package com.bandup.api.content.domain.exercise.aggregate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bandup.api.content.domain.exercise.entity.Gloss;
import com.bandup.api.content.domain.exercise.entity.Passage;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.service.PublishPolicy;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseContent;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseStatus;
import com.bandup.api.content.domain.exercise.valueobject.Format;
import com.bandup.api.content.domain.exercise.valueobject.GlossKey;
import com.bandup.api.content.domain.exercise.valueobject.GlossNote;
import com.bandup.api.content.domain.exercise.valueobject.GroupId;
import com.bandup.api.content.domain.exercise.valueobject.Level;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.OptionKey;
import com.bandup.api.content.domain.exercise.valueobject.Placement;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Revision;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.content.domain.exercise.valueobject.Timer;
import com.bandup.api.content.domain.exercise.valueobject.TopicId;
import com.bandup.api.shared.domain.ddd.AggregateRoot;
import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.numbering.NumberRange;
import com.bandup.api.shared.domain.numbering.QuestionNumbering;

/**
 * One exercise with its passage and questions (aggregate root; backend
 * requirements 4 and 5). Every change goes through it:
 *
 * <ul>
 *   <li>{@link #draft} creates it (DRAFT, revision 1); {@link #editContent}
 *       replaces its content. Both keep the passage's glosses and evidence in
 *       sync with the keys found in the HTML and check the rules that hold
 *       even for drafts (valid, unique keys; evidence that exists; options and
 *       limits that fit the question type; unique ids).</li>
 *   <li>Editing a PUBLISHED exercise raises its revision and must still pass
 *       the {@link PublishPolicy}, so learners never get a broken exercise.
 *       ARCHIVED exercises can't be edited.</li>
 *   <li>{@link #publish} applies the policy; {@link #unpublish} and
 *       {@link #archive} (final) change the status.</li>
 * </ul>
 *
 * Every failed change throws one {@link DomainException} with all
 * violations and leaves the exercise unchanged. Groups and questions keep
 * their ids across edits; new ones get theirs from the repository first.
 */
@AggregateRoot
public final class Exercise {

    private final ExerciseId id;
    private final Slug slug;
    private final Skill skill;
    private final LanguageCode targetLanguage;
    private final long version;
    private String title;
    private Format format;
    private Level level;
    private Timer timer;
    private Placement placement;
    private Set<TopicId> topics;
    private ExerciseStatus status;
    private Revision revision;
    private Instant publishedAt;
    private Passage passage;
    private List<QuestionGroup> groups;

    private Exercise(ExerciseId id, Slug slug, Skill skill, LanguageCode targetLanguage, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.skill = Objects.requireNonNull(skill, "skill");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        this.version = version;
    }

    /** A new DRAFT at revision 1. Only the rules that hold for drafts are checked. */
    public static Exercise draft(ExerciseId id, Slug slug, Skill skill, LanguageCode targetLanguage,
            ExerciseContent content) {
        Exercise exercise = new Exercise(id, slug, skill, targetLanguage, 0);
        exercise.status = ExerciseStatus.DRAFT;
        exercise.revision = Revision.first();
        List<Violation> violations = new ArrayList<>();
        Passage passage = buildPassage(content, violations);
        violations.addAll(structureViolations(content.title(), passage, content.groups()));
        throwIfAny(violations);
        exercise.take(content, passage);
        return exercise;
    }

    /**
     * Rebuilds a stored exercise (for the persistence adapter).
     *
     * @throws IllegalArgumentException if the stored data breaks the draft rules
     */
    public static Exercise restore(ExerciseId id, Slug slug, Skill skill, LanguageCode targetLanguage,
            String title, Format format, Level level, Timer timer, Placement placement, Set<TopicId> topics,
            ExerciseStatus status, Revision revision, long version, Instant publishedAt, Passage passage,
            List<QuestionGroup> groups) {
        List<Violation> violations = structureViolations(title, passage, groups);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Stored exercise " + slug + " is invalid: " + violations);
        }
        Exercise exercise = new Exercise(id, slug, skill, targetLanguage, version);
        exercise.title = title;
        exercise.format = Objects.requireNonNull(format, "format");
        exercise.level = level;
        exercise.timer = timer;
        exercise.placement = placement;
        exercise.topics = Set.copyOf(topics);
        exercise.status = Objects.requireNonNull(status, "status");
        exercise.revision = Objects.requireNonNull(revision, "revision");
        exercise.publishedAt = publishedAt;
        exercise.passage = passage;
        exercise.groups = List.copyOf(groups);
        return exercise;
    }

    /**
     * Replaces the editable content (backend requirements 4.2, 4.5, 5.2–5.4).
     *
     * @param policy applied when the exercise is PUBLISHED
     * @throws DomainException with every violation; the exercise is unchanged
     */
    public void editContent(ExerciseContent content, PublishPolicy policy) {
        List<Violation> violations = new ArrayList<>();
        if (status == ExerciseStatus.ARCHIVED) {
            throw DomainException.of("status", "An archived exercise can't be edited.");
        }
        Passage newPassage = buildPassage(content, violations);
        violations.addAll(structureViolations(content.title(), newPassage, content.groups()));
        if (violations.isEmpty() && status == ExerciseStatus.PUBLISHED) {
            Exercise candidate = copy();
            candidate.take(content, newPassage);
            violations.addAll(policy.check(candidate));
        }
        throwIfAny(violations);
        take(content, newPassage);
        if (status == ExerciseStatus.PUBLISHED) {
            revision = revision.next(); // attempts on the old content keep their revision
        }
    }

    /**
     * Shows the exercise to learners if it passes the policy.
     *
     * @throws DomainException with every violation; the exercise is unchanged
     */
    public void publish(PublishPolicy policy, Instant now) {
        if (status == ExerciseStatus.ARCHIVED) {
            throw DomainException.of("status", "An archived exercise can't be published.");
        }
        throwIfAny(policy.check(this));
        if (status != ExerciseStatus.PUBLISHED) {
            status = ExerciseStatus.PUBLISHED;
            publishedAt = Objects.requireNonNull(now, "now");
        }
    }

    /** Hides it from learners again (back to DRAFT). */
    public void unpublish() {
        if (status == ExerciseStatus.ARCHIVED) {
            throw DomainException.of("status", "An archived exercise can't be unpublished.");
        }
        status = ExerciseStatus.DRAFT;
    }

    /** Hides it for good, keeping it for the attempts that point at it. Final. */
    public void archive() {
        status = ExerciseStatus.ARCHIVED;
    }

    /** Each question's numbers, from order and points ("Choose TWO" takes two). */
    public Map<QuestionId, NumberRange> numbering() {
        List<Question> questions = new ArrayList<>();
        List<Integer> points = new ArrayList<>();
        groups.forEach(g -> g.questions().forEach(q -> {
            questions.add(q);
            points.add(g.pointsPerQuestion());
        }));
        List<NumberRange> ranges = QuestionNumbering.number(points);
        Map<QuestionId, NumberRange> numbers = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            numbers.put(questions.get(i).id(), ranges.get(i));
        }
        return numbers;
    }

    // ----------------------------------------------------------------- rules

    /** Builds the passage from the HTML's keys and the editor's notes; key problems go to {@code violations}. */
    private static Passage buildPassage(ExerciseContent content, List<Violation> violations) {
        MarkupKeys markup = content.markup();
        if (content.passageHtml() == null) {
            if (!markup.glosses().isEmpty() || !markup.evidence().isEmpty()) {
                throw new IllegalArgumentException("Markup keys without a passage");
            }
            return null;
        }
        List<Gloss> glosses = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MarkupKeys.GlossMark mark : markup.glosses()) {
            if (!GlossKey.isValid(mark.key())) {
                violations.add(new Violation("passageHtml", "Gloss key '" + mark.key() + "' must be 'g' followed by digits."));
            } else if (!seen.add(mark.key())) {
                violations.add(new Violation("passageHtml", "Gloss key '" + mark.key() + "' is used twice in the passage."));
            } else {
                GlossKey key = new GlossKey(mark.key());
                GlossNote note = content.glossNotes().getOrDefault(key, GlossNote.none());
                glosses.add(new Gloss(key, mark.surface(), note.lemma(), note.meanings()));
            }
        }
        Set<EvidenceKey> evidence = new LinkedHashSet<>();
        for (String key : markup.evidence()) {
            if (!EvidenceKey.isValid(key)) {
                violations.add(new Violation("passageHtml", "Evidence id '" + key + "' must be 'e' followed by digits."));
            } else if (!evidence.add(new EvidenceKey(key))) {
                violations.add(new Violation("passageHtml", "Evidence id '" + key + "' is used twice in the passage."));
            }
        }
        return new Passage(content.passageHtml(), glosses, evidence);
    }

    /** The rules that hold for every save, drafts included. */
    private static List<Violation> structureViolations(String title, Passage passage, List<QuestionGroup> groups) {
        List<Violation> violations = new ArrayList<>();
        if (title == null || title.isBlank()) {
            violations.add(new Violation("title", "Title is required."));
        } else if (title.length() > 200) {
            violations.add(new Violation("title", "Title can be at most 200 characters."));
        }
        Set<EvidenceKey> evidence = passage == null ? Set.of() : passage.evidence();
        Set<GroupId> groupIds = new HashSet<>();
        Set<QuestionId> questionIds = new HashSet<>();
        Map<QuestionId, String> labels = labels(groups);
        for (int g = 0; g < groups.size(); g++) {
            QuestionGroup group = groups.get(g);
            String field = "questionGroups[" + g + "]";
            QuestionFamily family = group.type().family();
            if (!groupIds.add(group.id())) {
                throw new IllegalArgumentException("Group " + group.id().value() + " appears twice");
            }
            if (group.chooseCount().isPresent() && family != QuestionFamily.MC_MULTI) {
                violations.add(new Violation(field + ".chooseCount", "Only \"Choose N\" questions have a number to choose."));
            }
            if (group.chooseCount().filter(n -> n < 1).isPresent()) {
                violations.add(new Violation(field + ".chooseCount", "The number to choose must be 1 or more."));
            }
            if (group.wordLimit().isPresent() && family != QuestionFamily.GAP) {
                violations.add(new Violation(field + ".maxWords", "Only gap questions have a word limit."));
            }
            if (!group.bank().isEmpty() && family != QuestionFamily.MATCH) {
                violations.add(new Violation(field + ".options", "Only matching questions share a list of options."));
            }
            duplicateKeys(group.bank()).forEach(key ->
                    violations.add(new Violation(field + ".options", "Option '" + key + "' is listed twice.")));
            for (int q = 0; q < group.questions().size(); q++) {
                Question question = group.questions().get(q);
                String questionField = field + ".questions[" + q + "]";
                String label = labels.get(question.id());
                if (!questionIds.add(question.id())) {
                    throw new IllegalArgumentException("Question " + question.id().value() + " appears twice");
                }
                if (!question.options().isEmpty() && !family.hasQuestionOptions()) {
                    violations.add(new Violation(questionField + ".options", label + ": only multiple-choice questions have their own options."));
                }
                duplicateKeys(question.options()).forEach(key ->
                        violations.add(new Violation(questionField + ".options", label + ": option '" + key + "' is listed twice.")));
                question.evidence().filter(key -> !evidence.contains(key)).ifPresent(key ->
                        violations.add(new Violation(questionField + ".evidence",
                                label + " points to evidence '" + key + "', which isn't in the passage.")));
            }
        }
        return violations;
    }

    /** "Q7" style labels for messages (numbers as the learner sees them). */
    private static Map<QuestionId, String> labels(List<QuestionGroup> groups) {
        List<Question> questions = new ArrayList<>();
        List<Integer> points = new ArrayList<>();
        groups.forEach(g -> g.questions().forEach(q -> {
            questions.add(q);
            points.add(g.pointsPerQuestion());
        }));
        List<NumberRange> ranges = QuestionNumbering.number(points);
        Map<QuestionId, String> labels = new HashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            labels.put(questions.get(i).id(), "Q" + ranges.get(i));
        }
        return labels;
    }

    private static Set<String> duplicateKeys(List<Option> options) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        options.stream().map(Option::key).map(OptionKey::value).forEach(k -> {
            if (!seen.add(k)) {
                duplicates.add(k);
            }
        });
        return duplicates;
    }

    private static void throwIfAny(List<Violation> violations) {
        if (!violations.isEmpty()) {
            throw new DomainException(violations);
        }
    }

    private void take(ExerciseContent content, Passage newPassage) {
        title = content.title();
        format = content.format();
        level = content.level();
        timer = content.timer();
        placement = content.placement();
        topics = content.topics();
        passage = newPassage;
        groups = content.groups();
    }

    private Exercise copy() {
        return restore(id, slug, skill, targetLanguage, title, format, level, timer, placement, topics, status,
                revision, version, publishedAt, passage, groups);
    }

    // ------------------------------------------------------------- accessors

    public ExerciseId id() {
        return id;
    }

    public Slug slug() {
        return slug;
    }

    public Skill skill() {
        return skill;
    }

    public LanguageCode targetLanguage() {
        return targetLanguage;
    }

    public String title() {
        return title;
    }

    public Format format() {
        return format;
    }

    public Optional<Level> level() {
        return Optional.ofNullable(level);
    }

    public Optional<Timer> timer() {
        return Optional.ofNullable(timer);
    }

    public Optional<Placement> placement() {
        return Optional.ofNullable(placement);
    }

    public Set<TopicId> topics() {
        return topics;
    }

    public ExerciseStatus status() {
        return status;
    }

    public Revision revision() {
        return revision;
    }

    /** The stored version this exercise was loaded with (optimistic locking; 0 for a new one). */
    public long version() {
        return version;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public Optional<Passage> passage() {
        return Optional.ofNullable(passage);
    }

    /** In display order. */
    public List<QuestionGroup> groups() {
        return groups;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Exercise e && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Exercise[" + id.value() + " " + slug + ", " + status + " r" + revision.value() + "]";
    }
}
