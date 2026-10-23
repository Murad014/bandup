package com.bandup.api.practice.infrastructure.readmodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.practice.application.PassageView;
import com.bandup.api.practice.application.port.LearnerPassages;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionFamily;
import com.bandup.api.shared.domain.language.LanguageChain;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;
import com.bandup.api.shared.domain.numbering.NumberRange;
import com.bandup.api.shared.domain.numbering.QuestionNumbering;

/**
 * {@link LearnerPassages} with explicit SQL. <b>Never</b> selects from
 * {@code question_answer}, {@code question_explanation} or the questions'
 * evidence links (a test checks every query of this class), so the learner
 * view can't leak answers before submit.
 */
@Component
@RequiredArgsConstructor
class JdbcLearnerPassages implements LearnerPassages {

    static final String EXERCISE = """
            select x.id, x.slug, x.title, x.duration_seconds, x.low_time_threshold_seconds, x.stage_id, x.position,
                   p.body_html, (select '/media/' || m.public_id from media m where m.id = x.cover_media_id) as cover_url
            from exercise x join reading_passage p on p.exercise_id = x.id
            where x.slug = ? and x.status = 'PUBLISHED' and x.skill = 'READING'""";

    static final String DIRECTION = """
            select direction from language where code = ?""";

    static final String STAGE_TITLES = """
            select language_code, title from stage_translation where stage_id = ?""";

    static final String GLOSSES = """
            select g.gloss_key, t.language_code, t.meaning
            from passage_gloss g join passage_gloss_translation t on t.gloss_id = g.id
            where g.exercise_id = ?""";

    static final String GROUPS = """
            select g.id, g.type_code, t.family, g.instruction_html, g.choose_count, g.max_words, g.numbers_allowed,
                   g.options_reusable
            from question_group g join question_type t on t.code = g.type_code
            where g.exercise_id = ?
            order by g.position""";

    static final String GROUP_TRANSLATIONS = """
            select gt.group_id, gt.language_code, gt.instruction_html
            from question_group_translation gt join question_group g on g.id = gt.group_id
            where g.exercise_id = ?""";

    static final String QUESTIONS = """
            select q.id, q.group_id, q.stem_text, q.stem_pre, q.stem_post
            from question q join question_group g on g.id = q.group_id
            where q.exercise_id = ?
            order by g.position, q.position""";

    static final String OPTIONS = """
            select o.group_id, o.question_id, o.option_key, o.label
            from question_option o
            left join question q on q.id = o.question_id
            join question_group g on g.id = coalesce(o.group_id, q.group_id)
            where g.exercise_id = ?
            order by o.position""";

    private final JdbcClient jdbc;

    private record Exercise(long id, String slug, String title, Integer duration, Integer lowTime, Long stageId,
            Integer position, String html, String coverUrl) {
    }

    private record GroupRow(long id, String type, QuestionFamily family, String instruction, Integer chooseCount,
            Integer maxWords, boolean numbersAllowed, boolean optionsReusable) {

        int pointsPerQuestion() {
            return family == QuestionFamily.MC_MULTI ? chooseCount : 1;
        }
    }

    private record QuestionRow(long id, long groupId, String text, String pre, String post) {
    }

    @Override
    public Optional<PassageView> find(String slug, LanguageChain help) {
        return jdbc.sql(EXERCISE).param(slug).query((rs, i) -> new Exercise(rs.getLong("id"), rs.getString("slug"),
                        rs.getString("title"), integer(rs, "duration_seconds"), integer(rs, "low_time_threshold_seconds"),
                        rs.getObject("stage_id", Long.class), integer(rs, "position"), rs.getString("body_html"),
                        rs.getString("cover_url")))
                .optional()
                .map(exercise -> view(exercise, help));
    }

    private PassageView view(Exercise exercise, LanguageChain help) {
        LanguageCode language = help.primary();
        String direction = jdbc.sql(DIRECTION).param(language.value()).query(String.class).optional().orElse("ltr");
        Optional<String> stageTitle = exercise.stageId() == null ? Optional.empty()
                : help.pick(translations(STAGE_TITLES, exercise.stageId(), "title"));

        Map<String, Translations<String>> glosses = keyedTranslations(GLOSSES, exercise.id(), "gloss_key", "meaning");
        Map<Long, Translations<String>> instructions = new HashMap<>();
        keyedTranslations(GROUP_TRANSLATIONS, exercise.id(), "group_id", "instruction_html")
                .forEach((groupId, t) -> instructions.put(Long.valueOf(groupId), t));

        List<GroupRow> groups = jdbc.sql(GROUPS).param(exercise.id()).query((rs, i) -> new GroupRow(rs.getLong("id"),
                rs.getString("type_code"), QuestionFamily.ofCode(rs.getString("family")), rs.getString("instruction_html"),
                integer(rs, "choose_count"), integer(rs, "max_words"), rs.getBoolean("numbers_allowed"),
                rs.getBoolean("options_reusable"))).list();
        List<QuestionRow> questions = jdbc.sql(QUESTIONS).param(exercise.id()).query((rs, i) -> new QuestionRow(
                rs.getLong("id"), rs.getLong("group_id"), rs.getString("stem_text"), rs.getString("stem_pre"),
                rs.getString("stem_post"))).list();
        Map<Long, List<PassageView.Option>> groupOptions = new HashMap<>();
        Map<Long, List<PassageView.Option>> questionOptions = new HashMap<>();
        jdbc.sql(OPTIONS).param(exercise.id()).query(rs -> {
            PassageView.Option option = new PassageView.Option(rs.getString("option_key"), rs.getString("label"));
            Long groupId = rs.getObject("group_id", Long.class);
            Map<Long, List<PassageView.Option>> owner = groupId != null ? groupOptions : questionOptions;
            owner.computeIfAbsent(groupId != null ? groupId : rs.getLong("question_id"), k -> new ArrayList<>()).add(option);
        });

        Map<Long, GroupRow> groupById = new HashMap<>();
        groups.forEach(g -> groupById.put(g.id(), g));
        List<NumberRange> numbers = QuestionNumbering.number(
                questions.stream().map(q -> groupById.get(q.groupId()).pointsPerQuestion()).toList());

        List<PassageView.Group> groupViews = new ArrayList<>();
        for (GroupRow group : groups) {
            List<PassageView.Question> questionViews = new ArrayList<>();
            int first = 0;
            int last = 0;
            for (int i = 0; i < questions.size(); i++) {
                QuestionRow q = questions.get(i);
                if (q.groupId() != group.id()) {
                    continue;
                }
                NumberRange range = numbers.get(i);
                first = first == 0 ? range.first() : first;
                last = range.last();
                questionViews.add(new PassageView.Question(range.first(),
                        range.size() > 1 ? Optional.of(range.last()) : Optional.empty(), group.type(),
                        Optional.ofNullable(q.text()), Optional.ofNullable(q.pre()), Optional.ofNullable(q.post()),
                        questionOptions.getOrDefault(q.id(), List.of())));
            }
            if (questionViews.isEmpty()) {
                continue; // an empty group can't be published; never show one
            }
            groupViews.add(new PassageView.Group(group.type(), first, last, group.instruction(),
                    help.pick(instructions.getOrDefault(group.id(), Translations.empty())),
                    Optional.ofNullable(group.maxWords()), group.numbersAllowed(),
                    Optional.ofNullable(group.family() == QuestionFamily.MC_MULTI ? group.chooseCount() : null),
                    group.optionsReusable(), groupOptions.getOrDefault(group.id(), List.of()), questionViews));
        }

        return new PassageView(exercise.slug(), language.value(), direction, exercise.title(),
                Optional.ofNullable(exercise.duration()), Optional.ofNullable(exercise.lowTime()), stageTitle,
                Optional.ofNullable(exercise.position()), exercise.html(), help.pickAll(glosses), groupViews,
                exercise.coverUrl(), topicsOf(exercise.id()));
    }

    private List<String> topicsOf(long exerciseId) {
        return jdbc.sql("""
                select t.slug from exercise_topic et join topic t on t.id = et.topic_id
                where et.exercise_id = ? order by t.position, t.slug""").param(exerciseId).query(String.class).list();
    }

    private Translations<String> translations(String sql, long ownerId, String column) {
        Map<LanguageCode, String> values = new LinkedHashMap<>();
        jdbc.sql(sql).param(ownerId).query(rs -> {
            values.put(LanguageCode.of(rs.getString("language_code")), rs.getString(column));
        });
        return Translations.of(values);
    }

    private Map<String, Translations<String>> keyedTranslations(String sql, long exerciseId, String keyColumn,
            String valueColumn) {
        Map<String, Map<LanguageCode, String>> rows = new LinkedHashMap<>();
        jdbc.sql(sql).param(exerciseId).query(rs -> {
            rows.computeIfAbsent(rs.getString(keyColumn), k -> new LinkedHashMap<>())
                    .put(LanguageCode.of(rs.getString("language_code")), rs.getString(valueColumn));
        });
        Map<String, Translations<String>> result = new LinkedHashMap<>();
        rows.forEach((key, values) -> result.put(key, Translations.of(values)));
        return result;
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, Integer.class);
    }
}
