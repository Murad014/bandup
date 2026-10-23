package com.bandup.api.content.infrastructure.persistence;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.Gloss;
import com.bandup.api.content.domain.exercise.entity.Passage;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.repository.ExerciseRepository;
import com.bandup.api.content.domain.exercise.valueobject.AnswerKey;
import com.bandup.api.content.domain.exercise.valueobject.EvidenceKey;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseId;
import com.bandup.api.content.domain.exercise.valueobject.ExerciseStatus;
import com.bandup.api.content.domain.exercise.valueobject.Explanation;
import com.bandup.api.content.domain.exercise.valueobject.Format;
import com.bandup.api.content.domain.exercise.valueobject.GlossKey;
import com.bandup.api.content.domain.exercise.valueobject.GlossMeaning;
import com.bandup.api.content.domain.exercise.valueobject.GroupId;
import com.bandup.api.content.domain.exercise.valueobject.Level;
import com.bandup.api.content.domain.exercise.valueobject.MediaId;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.OptionKey;
import com.bandup.api.content.domain.exercise.valueobject.Placement;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.QuestionType;
import com.bandup.api.content.domain.exercise.valueobject.Revision;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.content.domain.exercise.valueobject.Slug;
import com.bandup.api.content.domain.exercise.valueobject.StageId;
import com.bandup.api.content.domain.exercise.valueobject.Stem;
import com.bandup.api.content.domain.exercise.valueobject.TextSource;
import com.bandup.api.content.domain.exercise.valueobject.Timer;
import com.bandup.api.content.domain.exercise.valueobject.TopicId;
import com.bandup.api.content.domain.exercise.valueobject.WordLimit;
import com.bandup.api.shared.application.ConflictException;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

/**
 * {@link ExerciseRepository} on JPA. An exercise is loaded from its twelve
 * tables and saved whole, in an explicit order, because none of the schema's
 * unique keys are deferrable and Hibernate would otherwise insert before it
 * deletes:
 *
 * <ol>
 *   <li>Lock the exercise row ({@code FOR UPDATE}), check its version against
 *       the one the exercise was loaded with (stale → {@link ConflictException}),
 *       raise it by one.</li>
 *   <li>Remove the rows nothing points at: topics, translations, options,
 *       answers, explanations (re-inserted at the end).</li>
 *   <li>Evidence and glosses: matched by key; new keys are inserted, changed
 *       glosses updated.</li>
 *   <li>Groups and questions: matched by id. Existing ones first move to
 *       temporary negative positions; removed questions are deleted; groups,
 *       then questions get their final position, group, stem and evidence
 *       row; removed groups go last (deleting a group cascades to its
 *       questions, so moved questions must have left it).</li>
 *   <li>Evidence no longer in the passage is deleted, the passage row written,
 *       and the replaceable rows inserted again.</li>
 * </ol>
 *
 * Concurrent saves of one exercise therefore run one after another, and the
 * second one is rejected as stale.
 */
@Repository
@RequiredArgsConstructor
class JpaExerciseRepository implements ExerciseRepository {

    private final EntityManager em;
    private final Clock clock;

    // ------------------------------------------------------------------ ids

    @Override
    public ExerciseId nextExerciseId() {
        return new ExerciseId(nextval("exercise"));
    }

    @Override
    public GroupId nextGroupId() {
        return new GroupId(nextval("question_group"));
    }

    @Override
    public QuestionId nextQuestionId() {
        return new QuestionId(nextval("question"));
    }

    private long nextval(String table) {
        return ((Number) em.createNativeQuery("select nextval(pg_get_serial_sequence(?1, 'id'))")
                .setParameter(1, table).getSingleResult()).longValue();
    }

    // ----------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(Slug slug) {
        return !em.createQuery("select e.id from ExerciseEntity e where e.slug = :slug", Long.class)
                .setParameter("slug", slug.value()).getResultList().isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Exercise> findBySlug(Slug slug) {
        return em.createQuery("select e from ExerciseEntity e where e.slug = :slug", ExerciseEntity.class)
                .setParameter("slug", slug.value()).getResultStream().findFirst().map(this::load);
    }

    private Exercise load(ExerciseEntity row) {
        long id = row.id;
        Map<String, QuestionFamily> families = questionFamilies();

        ReadingPassageEntity passageRow = em.find(ReadingPassageEntity.class, id);
        List<PassageEvidenceEntity> evidenceRows = byExercise(PassageEvidenceEntity.class, id);
        Map<Long, String> evidenceKeyById = evidenceRows.stream()
                .collect(Collectors.toMap(e -> e.id, e -> e.evidenceKey));
        Passage passage = passageRow == null ? null : passage(passageRow, evidenceRows, byExercise(PassageGlossEntity.class, id));

        List<QuestionGroupEntity> groupRows = byExercise(QuestionGroupEntity.class, id);
        groupRows.sort(Comparator.comparingInt(g -> g.position));
        List<QuestionEntity> questionRows = byExercise(QuestionEntity.class, id);
        questionRows.sort(Comparator.comparingInt(q -> q.position));
        Set<Long> groupIds = groupRows.stream().map(g -> g.id).collect(Collectors.toSet());
        Set<Long> questionIds = questionRows.stream().map(q -> q.id).collect(Collectors.toSet());

        Map<Long, List<QuestionOptionEntity>> bankByGroup = new HashMap<>();
        Map<Long, List<QuestionOptionEntity>> optionsByQuestion = new HashMap<>();
        for (QuestionOptionEntity option : options(groupIds, questionIds)) {
            (option.groupId != null ? bankByGroup.computeIfAbsent(option.groupId, k -> new ArrayList<>())
                    : optionsByQuestion.computeIfAbsent(option.questionId, k -> new ArrayList<>())).add(option);
        }
        Map<Long, List<String>> answers = new HashMap<>();
        in(QuestionAnswerEntity.class, "questionId", questionIds).stream()
                .sorted(Comparator.comparingInt(a -> a.position))
                .forEach(a -> answers.computeIfAbsent(a.questionId, k -> new ArrayList<>()).add(a.value));
        Map<Long, Map<LanguageCode, Explanation>> explanations = new HashMap<>();
        in(QuestionExplanationEntity.class, "questionId", questionIds).forEach(x -> explanations
                .computeIfAbsent(x.questionId, k -> new LinkedHashMap<>())
                .put(LanguageCode.of(x.languageCode), new Explanation(new SanitizedHtml(x.explanationHtml),
                        TextSource.valueOf(x.source), x.reviewed)));
        Map<Long, Map<LanguageCode, SanitizedHtml>> instructions = new HashMap<>();
        in(QuestionGroupTranslationEntity.class, "groupId", groupIds).forEach(t -> instructions
                .computeIfAbsent(t.groupId, k -> new LinkedHashMap<>())
                .put(LanguageCode.of(t.languageCode), new SanitizedHtml(t.instructionHtml)));

        List<QuestionGroup> groups = new ArrayList<>();
        for (QuestionGroupEntity g : groupRows) {
            List<Question> questions = questionRows.stream().filter(q -> q.groupId.equals(g.id))
                    .map(q -> new Question(new QuestionId(q.id), stem(q),
                            q.evidenceId == null ? null : new EvidenceKey(evidenceKeyById.get(q.evidenceId)),
                            toOptions(optionsByQuestion.get(q.id)), new AnswerKey(answers.getOrDefault(q.id, List.of())),
                            Translations.of(explanations.getOrDefault(q.id, Map.of()))))
                    .toList();
            QuestionFamily family = families.get(g.typeCode);
            groups.add(new QuestionGroup(new GroupId(g.id), new QuestionType(g.typeCode, family),
                    new SanitizedHtml(g.instructionHtml), Translations.of(instructions.getOrDefault(g.id, Map.of())),
                    g.chooseCount, g.maxWords == null ? null : new WordLimit(g.maxWords, g.numbersAllowed),
                    g.optionsReusable, g.mediaId == null ? null : new MediaId(g.mediaId),
                    toOptions(bankByGroup.get(g.id)), questions));
        }

        Set<TopicId> topics = byExercise(ExerciseTopicEntity.class, id).stream().map(t -> new TopicId(t.topicId))
                .collect(Collectors.toSet());
        return Exercise.restore(new ExerciseId(id), new Slug(row.slug), Skill.valueOf(row.skill),
                LanguageCode.of(row.targetLanguage), row.title, Format.valueOf(row.format),
                row.level == null ? null : Level.valueOf(row.level),
                row.durationSeconds == null ? null : new Timer(row.durationSeconds, row.lowTimeThresholdSeconds),
                row.stageId == null ? null : new Placement(new StageId(row.stageId), row.position), topics,
                ExerciseStatus.valueOf(row.status), new Revision(row.revision), row.version, row.publishedAt, passage,
                groups, row.coverMediaId == null ? null : new MediaId(row.coverMediaId));
    }

    /** Glosses and evidence in the order they appear in the HTML (as the domain keeps them). */
    private Passage passage(ReadingPassageEntity row, List<PassageEvidenceEntity> evidenceRows,
            List<PassageGlossEntity> glossRows) {
        String html = row.bodyHtml;
        Map<Long, Map<LanguageCode, GlossMeaning>> meanings = new HashMap<>();
        in(PassageGlossTranslationEntity.class, "glossId", glossRows.stream().map(g -> g.id).collect(Collectors.toSet()))
                .forEach(t -> meanings.computeIfAbsent(t.glossId, k -> new LinkedHashMap<>())
                        .put(LanguageCode.of(t.languageCode), new GlossMeaning(t.meaning, TextSource.valueOf(t.source), t.reviewed)));
        List<Gloss> glosses = glossRows.stream()
                .sorted(Comparator.comparingInt(g -> positionIn(html, "data-g=\"" + g.glossKey + "\"")))
                .map(g -> new Gloss(new GlossKey(g.glossKey), g.surface, g.lemma,
                        Translations.of(meanings.getOrDefault(g.id, Map.of()))))
                .toList();
        List<EvidenceKey> evidence = evidenceRows.stream()
                .sorted(Comparator.comparingInt(e -> positionIn(html, "id=\"" + e.evidenceKey + "\"")))
                .map(e -> new EvidenceKey(e.evidenceKey)).toList();
        return new Passage(new SanitizedHtml(html), glosses, new LinkedHashSet<>(evidence));
    }

    private static int positionIn(String html, String marker) {
        int at = html.indexOf(marker);
        return at < 0 ? Integer.MAX_VALUE : at;
    }

    private static Stem stem(QuestionEntity q) {
        return q.stemPre != null ? new Stem.Gap(q.stemPre, q.stemPost) : new Stem.Text(q.stemText);
    }

    private static List<Option> toOptions(List<QuestionOptionEntity> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().sorted(Comparator.comparingInt(o -> o.position))
                .map(o -> new Option(new OptionKey(o.optionKey), o.label)).toList();
    }

    private Map<String, QuestionFamily> questionFamilies() {
        Map<String, QuestionFamily> families = new HashMap<>();
        for (Object row : em.createNativeQuery("select code, family from question_type").getResultList()) {
            Object[] columns = (Object[]) row;
            families.put((String) columns[0], QuestionFamily.ofCode((String) columns[1]));
        }
        return families;
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional
    public long save(Exercise exercise) {
        long id = exercise.id().value();
        ExerciseEntity row = lockedRow(id);
        if (row == null) {
            if (exercise.version() != 0) {
                throw stale();
            }
            row = new ExerciseEntity();
            row.id = id;
            row.version = 0;
            copyExercise(exercise, row);
            em.persist(row);
        } else {
            if (row.version != exercise.version()) {
                throw stale();
            }
            row.version = row.version + 1;
            copyExercise(exercise, row);
        }
        em.flush();

        removeReplaceableRows(id);
        Map<String, Long> evidenceIds = saveEvidenceAndGlosses(exercise, id);
        saveGroupsAndQuestions(exercise, id, evidenceIds);
        removeEvidenceNoLongerInThePassage(exercise, id);
        savePassage(exercise, id);
        insertReplaceableRows(exercise, id, evidenceIds);
        em.flush();
        return row.version;
    }

    @Override
    @Transactional
    public void delete(Exercise exercise) {
        ExerciseEntity row = lockedRow(exercise.id().value());
        if (row == null || row.version != exercise.version()) {
            throw stale();
        }
        em.flush();
        em.clear(); // the database cascades to every child table
        em.createNativeQuery("delete from exercise where id = ?1").setParameter(1, exercise.id().value()).executeUpdate();
    }

    /**
     * The exercise row, locked ({@code FOR UPDATE}) and re-read: if it was
     * loaded earlier in this transaction, a plain locking find would keep the
     * old version in memory and miss a save that committed meanwhile.
     */
    private ExerciseEntity lockedRow(long id) {
        ExerciseEntity row = em.find(ExerciseEntity.class, id);
        if (row != null) {
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        }
        return row;
    }

    private static ConflictException stale() {
        return new ConflictException("This exercise was changed by someone else. Reload it and try again.");
    }

    private void copyExercise(Exercise exercise, ExerciseEntity row) {
        row.slug = exercise.slug().value();
        row.skill = exercise.skill().name();
        row.targetLanguage = exercise.targetLanguage().value();
        row.stageId = exercise.placement().map(p -> p.stage().value()).orElse(null);
        row.position = exercise.placement().map(Placement::position).orElse(null);
        row.title = exercise.title();
        row.format = exercise.format().name();
        row.level = exercise.level().map(Level::name).orElse(null);
        row.durationSeconds = exercise.timer().map(Timer::durationSeconds).orElse(null);
        row.lowTimeThresholdSeconds = exercise.timer().flatMap(Timer::lowTimeThreshold).orElse(null);
        row.status = exercise.status().name();
        row.revision = exercise.revision().value();
        row.publishedAt = exercise.publishedAt().orElse(null);
        row.coverMediaId = exercise.cover().map(MediaId::value).orElse(null);
        row.updatedAt = clock.instant();
    }

    /** Rows nothing points at: removed now, inserted again at the end. */
    private void removeReplaceableRows(long id) {
        Set<Long> groupIds = ids(byExercise(QuestionGroupEntity.class, id), g -> g.id);
        Set<Long> questionIds = ids(byExercise(QuestionEntity.class, id), q -> q.id);
        Set<Long> glossIds = ids(byExercise(PassageGlossEntity.class, id), g -> g.id);
        byExercise(ExerciseTopicEntity.class, id).forEach(em::remove);
        options(groupIds, questionIds).forEach(em::remove);
        in(QuestionAnswerEntity.class, "questionId", questionIds).forEach(em::remove);
        in(QuestionExplanationEntity.class, "questionId", questionIds).forEach(em::remove);
        in(QuestionGroupTranslationEntity.class, "groupId", groupIds).forEach(em::remove);
        in(PassageGlossTranslationEntity.class, "glossId", glossIds).forEach(em::remove);
        em.flush();
    }

    /** Inserts new evidence keys and syncs glosses by key; returns every evidence key's row id. */
    private Map<String, Long> saveEvidenceAndGlosses(Exercise exercise, long id) {
        Optional<Passage> passage = exercise.passage();
        Map<String, PassageEvidenceEntity> evidence = byExercise(PassageEvidenceEntity.class, id).stream()
                .collect(Collectors.toMap(e -> e.evidenceKey, Function.identity()));
        passage.ifPresent(p -> p.evidence().stream().map(EvidenceKey::value).filter(k -> !evidence.containsKey(k))
                .forEach(key -> {
                    PassageEvidenceEntity row = new PassageEvidenceEntity();
                    row.exerciseId = id;
                    row.evidenceKey = key;
                    em.persist(row);
                    evidence.put(key, row);
                }));

        Map<String, PassageGlossEntity> glossRows = byExercise(PassageGlossEntity.class, id).stream()
                .collect(Collectors.toMap(g -> g.glossKey, Function.identity()));
        Set<String> keep = new HashSet<>();
        for (Gloss gloss : passage.map(Passage::glosses).orElse(List.of())) {
            PassageGlossEntity row = glossRows.get(gloss.key().value());
            boolean isNew = row == null;
            if (isNew) {
                row = new PassageGlossEntity();
                row.exerciseId = id;
                row.glossKey = gloss.key().value();
            }
            row.surface = gloss.surface();
            row.lemma = gloss.lemma().orElse(null);
            if (isNew) {
                em.persist(row); // identity column: inserted right away, so every field must be set first
            }
            keep.add(gloss.key().value());
        }
        glossRows.values().stream().filter(g -> !keep.contains(g.glossKey)).forEach(em::remove);
        em.flush();
        Map<String, Long> ids = new HashMap<>();
        evidence.forEach((key, row) -> ids.put(key, row.id));
        return ids;
    }

    private void saveGroupsAndQuestions(Exercise exercise, long id, Map<String, Long> evidenceIds) {
        Map<Long, QuestionGroupEntity> groupRows = byExercise(QuestionGroupEntity.class, id).stream()
                .collect(Collectors.toMap(g -> g.id, Function.identity()));
        Map<Long, QuestionEntity> questionRows = byExercise(QuestionEntity.class, id).stream()
                .collect(Collectors.toMap(q -> q.id, Function.identity()));

        // Free every position first: unique (exercise, position) and (group, position) aren't deferrable.
        groupRows.values().forEach(g -> g.position = (int) -g.id);
        questionRows.values().forEach(q -> q.position = (int) -q.id);
        em.flush();

        Set<Long> keptGroups = new HashSet<>();
        Set<Long> keptQuestions = new HashSet<>();
        exercise.groups().forEach(g -> {
            keptGroups.add(g.id().value());
            g.questions().forEach(q -> keptQuestions.add(q.id().value()));
        });
        questionRows.values().stream().filter(q -> !keptQuestions.contains(q.id)).forEach(em::remove);
        em.flush();

        for (int i = 0; i < exercise.groups().size(); i++) {
            QuestionGroup group = exercise.groups().get(i);
            QuestionGroupEntity row = groupRows.get(group.id().value());
            boolean isNew = row == null;
            if (isNew) {
                row = new QuestionGroupEntity();
                row.id = group.id().value();
                row.exerciseId = id;
            }
            row.position = i + 1;
            row.typeCode = group.type().code();
            row.instructionHtml = group.instruction().html();
            row.chooseCount = group.chooseCount().orElse(null);
            row.maxWords = group.wordLimit().map(WordLimit::maxWords).orElse(null);
            row.numbersAllowed = group.wordLimit().map(WordLimit::numbersAllowed).orElse(false);
            row.optionsReusable = group.optionsReusable();
            row.mediaId = group.media().map(MediaId::value).orElse(null);
            if (isNew) {
                em.persist(row);
            }
        }
        em.flush();

        for (QuestionGroup group : exercise.groups()) {
            for (int j = 0; j < group.questions().size(); j++) {
                Question question = group.questions().get(j);
                QuestionEntity row = questionRows.get(question.id().value());
                boolean isNew = row == null;
                if (isNew) {
                    row = new QuestionEntity();
                    row.id = question.id().value();
                    row.exerciseId = id;
                }
                row.groupId = group.id().value();
                row.position = j + 1;
                switch (question.stem()) {
                    case Stem.Text text -> {
                        row.stemText = text.text();
                        row.stemPre = null;
                        row.stemPost = null;
                    }
                    case Stem.Gap gap -> {
                        row.stemText = null;
                        row.stemPre = gap.pre();
                        row.stemPost = gap.post().isEmpty() ? null : gap.post();
                    }
                }
                row.evidenceId = question.evidence().map(key -> evidenceIds.get(key.value())).orElse(null);
                if (isNew) {
                    em.persist(row);
                }
            }
        }
        em.flush();
        // Only now: deleting a group cascades to the questions still in it, and moved questions have left it.
        groupRows.values().stream().filter(g -> !keptGroups.contains(g.id)).forEach(em::remove);
        em.flush();
    }

    private void removeEvidenceNoLongerInThePassage(Exercise exercise, long id) {
        Set<String> keep = exercise.passage().map(p -> p.evidence().stream().map(EvidenceKey::value)
                .collect(Collectors.toSet())).orElse(Set.of());
        byExercise(PassageEvidenceEntity.class, id).stream().filter(e -> !keep.contains(e.evidenceKey)).forEach(em::remove);
        em.flush();
    }

    private void savePassage(Exercise exercise, long id) {
        ReadingPassageEntity row = em.find(ReadingPassageEntity.class, id);
        if (exercise.passage().isEmpty()) {
            if (row != null) {
                em.remove(row);
            }
            return;
        }
        Passage passage = exercise.passage().get();
        boolean isNew = row == null;
        if (isNew) {
            row = new ReadingPassageEntity();
            row.exerciseId = id;
        }
        row.bodyHtml = passage.html().html();
        row.wordCount = passage.wordCount();
        if (isNew) {
            em.persist(row);
        }
    }

    private void insertReplaceableRows(Exercise exercise, long id, Map<String, Long> evidenceIds) {
        exercise.topics().forEach(topic -> {
            ExerciseTopicEntity row = new ExerciseTopicEntity();
            row.exerciseId = id;
            row.topicId = topic.value();
            em.persist(row);
        });
        Map<String, Long> glossIds = byExercise(PassageGlossEntity.class, id).stream()
                .collect(Collectors.toMap(g -> g.glossKey, g -> g.id));
        exercise.passage().ifPresent(p -> p.glosses().forEach(gloss -> gloss.meanings().asMap().forEach((language, meaning) -> {
            PassageGlossTranslationEntity row = new PassageGlossTranslationEntity();
            row.glossId = glossIds.get(gloss.key().value());
            row.languageCode = language.value();
            row.meaning = meaning.text();
            row.source = meaning.source().name();
            row.reviewed = meaning.reviewed();
            em.persist(row);
        })));
        for (QuestionGroup group : exercise.groups()) {
            long groupId = group.id().value();
            group.instructionTranslations().asMap().forEach((language, html) -> {
                QuestionGroupTranslationEntity row = new QuestionGroupTranslationEntity();
                row.groupId = groupId;
                row.languageCode = language.value();
                row.instructionHtml = html.html();
                em.persist(row);
            });
            persistOptions(group.bank(), groupId, null);
            for (Question question : group.questions()) {
                long questionId = question.id().value();
                persistOptions(question.options(), null, questionId);
                List<String> answer = question.answer().values();
                for (int i = 0; i < answer.size(); i++) {
                    QuestionAnswerEntity row = new QuestionAnswerEntity();
                    row.questionId = questionId;
                    row.value = answer.get(i);
                    row.position = i + 1;
                    em.persist(row);
                }
                question.explanations().asMap().forEach((language, explanation) -> {
                    QuestionExplanationEntity row = new QuestionExplanationEntity();
                    row.questionId = questionId;
                    row.languageCode = language.value();
                    row.explanationHtml = explanation.html().html();
                    row.source = explanation.source().name();
                    row.reviewed = explanation.reviewed();
                    em.persist(row);
                });
            }
        }
    }

    private void persistOptions(List<Option> options, Long groupId, Long questionId) {
        for (int i = 0; i < options.size(); i++) {
            QuestionOptionEntity row = new QuestionOptionEntity();
            row.groupId = groupId;
            row.questionId = questionId;
            row.optionKey = options.get(i).key().value();
            row.label = options.get(i).label();
            row.position = i + 1;
            em.persist(row);
        }
    }

    // -------------------------------------------------------------- queries

    private <T> List<T> byExercise(Class<T> type, long exerciseId) {
        return new ArrayList<>(em.createQuery("select x from " + type.getSimpleName() + " x where x.exerciseId = :id", type)
                .setParameter("id", exerciseId).getResultList());
    }

    private <T> List<T> in(Class<T> type, String column, Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select x from " + type.getSimpleName() + " x where x." + column + " in :ids", type)
                .setParameter("ids", ids).getResultList();
    }

    private List<QuestionOptionEntity> options(Set<Long> groupIds, Set<Long> questionIds) {
        List<QuestionOptionEntity> options = new ArrayList<>(in(QuestionOptionEntity.class, "groupId", groupIds));
        options.addAll(in(QuestionOptionEntity.class, "questionId", questionIds));
        return options;
    }

    private static <T> Set<Long> ids(List<T> rows, Function<T, Long> id) {
        return rows.stream().map(id).collect(Collectors.toSet());
    }
}
