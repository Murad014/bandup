package com.bandup.api.vocabulary.infrastructure.readmodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.LearnerWords;
import com.bandup.api.vocabulary.application.view.VocabularyViews.Counts;
import com.bandup.api.vocabulary.application.view.VocabularyViews.MeaningView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SavedWordView;
import com.bandup.api.vocabulary.application.view.VocabularyViews.SourceView;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.infrastructure.persistence.CardJson;

/** {@link LearnerWords} with SQL over {@code vocab_saved_word}, {@code vocab_word} and the source passage. */
@Component
@RequiredArgsConstructor
class JdbcLearnerWords implements LearnerWords {

    private static final String SELECT = """
            select s.id, s.state, s.box, s.due_on, s.added_on, s.source_sentence, e.slug, e.title, w.status, w.content,
                   coalesce(w.lemma, (select min(f.form) from vocab_word_form f where f.word_id = w.id)) as word,
                   array(select f.form from vocab_word_form f where f.word_id = w.id order by f.form) as forms
            from vocab_saved_word s
            join vocab_word w on w.id = s.word_id
            join app_user u on u.id = s.user_id
            left join exercise e on e.id = s.source_exercise_id
            """;
    private static final String FILTER = """
            where u.public_id = :learner
              and (cast(:state as varchar) is null or s.state = :state)
              and (cast(:exercise as varchar) is null
                   or s.source_exercise_id = (select x.id from exercise x where x.slug = :exercise))
              and (cast(:query as varchar) is null
                   or coalesce(w.lemma, '') like :like
                   or exists (select 1 from vocab_word_form f where f.word_id = w.id and f.form like :like)
                   or lower(coalesce(w.content -> 'meaning' ->> 'en', '')) like :like)
            """;

    private final JdbcClient jdbc;

    @Override
    public List<SavedWordView> page(LearnerId learner, String state,
            String query, String exercise, int page, int size) {
        return jdbc.sql(SELECT + FILTER + " order by s.created_at desc, s.id limit :size offset :offset")
                .params(filter(learner.value(), state, query, exercise)).param("size", size).param("offset", page * size)
                .query(JdbcLearnerWords::row).list();
    }

    @Override
    public long total(LearnerId learner, String state, String query, String exercise) {
        return jdbc.sql("select count(*) from vocab_saved_word s join vocab_word w on w.id = s.word_id"
                        + " join app_user u on u.id = s.user_id " + FILTER)
                .params(filter(learner.value(), state, query, exercise)).query(Long.class).single();
    }

    @Override
    public List<SavedWordView> byIds(LearnerId learner,
            Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " where u.public_id = :learner and s.id in (:ids) order by s.created_at desc")
                .param("learner", learner.value()).param("ids", ids).query(JdbcLearnerWords::row).list();
    }

    @Override
    public Counts counts(LearnerId learner) {
        Map<String, Integer> byState = new java.util.HashMap<>();
        jdbc.sql("""
                select s.state, count(*) as n from vocab_saved_word s join app_user u on u.id = s.user_id
                where u.public_id = ? group by s.state""").param(learner.value())
                .query((rs, n) -> Map.entry(rs.getString("state"), rs.getInt("n"))).list()
                .forEach(e -> byState.put(e.getKey(), e.getValue()));
        int fresh = byState.getOrDefault("NEW", 0);
        int learning = byState.getOrDefault("LEARNING", 0);
        int mastered = byState.getOrDefault("MASTERED", 0);
        return new Counts(fresh + learning + mastered, fresh, learning, mastered);
    }

    @Override
    public int due(LearnerId learner, LocalDate today) {
        return jdbc.sql("""
                select count(*) from vocab_saved_word s join app_user u on u.id = s.user_id
                join vocab_word w on w.id = s.word_id
                where u.public_id = ? and s.state = 'LEARNING' and s.due_on <= ? and w.status = 'READY'""")
                .params(learner.value(), today).query(Integer.class).single();
    }

    @Override
    public int newReady(LearnerId learner) {
        return jdbc.sql("""
                select count(*) from vocab_saved_word s join app_user u on u.id = s.user_id
                join vocab_word w on w.id = s.word_id
                where u.public_id = ? and s.state = 'NEW' and w.status = 'READY'""")
                .param(learner.value()).query(Integer.class).single();
    }

    @Override
    public List<UUID> learnable(LearnerId learner, String exercise, int limit) {
        return jdbc.sql("""
                select s.id from vocab_saved_word s join app_user u on u.id = s.user_id
                join vocab_word w on w.id = s.word_id
                left join exercise e on e.id = s.source_exercise_id
                where u.public_id = :learner and s.state = 'NEW' and w.status = 'READY'
                  and (cast(:exercise as varchar) is null or e.slug = :exercise)
                order by s.created_at, s.id limit :limit""")
                .param("learner", learner.value()).param("exercise", exercise).param("limit", limit)
                .query(UUID.class).list();
    }

    @Override
    public List<UUID> due(LearnerId learner, LocalDate today, int limit) {
        return jdbc.sql("""
                select s.id from vocab_saved_word s join app_user u on u.id = s.user_id
                join vocab_word w on w.id = s.word_id
                where u.public_id = ? and s.state = 'LEARNING' and s.due_on <= ? and w.status = 'READY'
                order by s.due_on, s.created_at, s.id limit ?""")
                .params(learner.value(), today, limit).query(UUID.class).list();
    }

    @Override
    public List<Recallable> recallable(LearnerId learner) {
        return jdbc.sql("""
                select s.id, w.content -> 'meaning' ->> 'az' as az, w.lemma,
                       (select string_agg(f.form, '|' order by f.form) from vocab_word_form f where f.word_id = w.id) as forms
                from vocab_saved_word s join app_user u on u.id = s.user_id
                join vocab_word w on w.id = s.word_id
                where u.public_id = ? and s.state in ('LEARNING', 'MASTERED') and w.status = 'READY'
                order by s.created_at, s.id""")
                .param(learner.value())
                .query((rs, n) -> {
                    LinkedHashSet<String> spellings = new LinkedHashSet<>();
                    spellings.add(rs.getString("lemma"));
                    String forms = rs.getString("forms");
                    if (forms != null) {
                        spellings.addAll(List.of(forms.split("\\|")));
                    }
                    return new Recallable(rs.getObject("id", UUID.class), rs.getString("az"), List.copyOf(spellings));
                }).list();
    }

    private static Map<String, Object> filter(UUID learner, String state, String query, String exercise) {
        String q = query == null || query.isBlank() ? null : query.strip().toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("learner", learner);
        params.put("state", state);
        params.put("query", q);
        params.put("exercise", exercise == null || exercise.isBlank() ? null : exercise.strip());
        params.put("like", q == null ? "" : "%" + q.replace("%", "").replace("_", "") + "%");
        return params;
    }

    private static SavedWordView row(ResultSet rs, int n) throws SQLException {
        String json = rs.getString("content");
        CardContent c = json == null ? null : CardJson.read(json);
        int box = rs.getInt("box");
        boolean noBox = rs.wasNull();
        java.sql.Date due = rs.getDate("due_on");
        java.sql.Array forms = rs.getArray("forms");
        List<String> formList = forms == null ? List.of() : List.of((String[]) forms.getArray());
        return new SavedWordView(rs.getObject("id", UUID.class), rs.getString("word"), formList, c == null ? null : c.pos(),
                c == null ? null : c.level(), c == null ? null : c.ipa(),
                c == null ? null : new MeaningView(c.meaning().en(), c.meaning().az()),
                new SourceView(rs.getString("slug"), rs.getString("title"), rs.getString("source_sentence")),
                rs.getDate("added_on").toLocalDate(), rs.getString("state"), noBox ? null : box,
                due == null ? null : due.toLocalDate(), rs.getString("status"));
    }
}
