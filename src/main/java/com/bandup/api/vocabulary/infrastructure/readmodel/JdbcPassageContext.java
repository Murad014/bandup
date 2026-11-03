package com.bandup.api.vocabulary.infrastructure.readmodel;

import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.PassageContext;

/** {@link PassageContext}: a passage's glosses and title, read with SQL (Content's classes are never used). */
@Component
@RequiredArgsConstructor
class JdbcPassageContext implements PassageContext {

    private final JdbcClient jdbc;

    @Override
    public Optional<Gloss> gloss(String exerciseSlug, String form) {
        return jdbc.sql("""
                select (select t.meaning from passage_gloss_translation t where t.gloss_id = g.id and t.language_code = 'en') as en,
                       (select t.meaning from passage_gloss_translation t where t.gloss_id = g.id and t.language_code = 'az') as az
                from passage_gloss g join exercise e on e.id = g.exercise_id
                where e.slug = ? and (lower(g.surface) = ? or lower(g.lemma) = ?)
                order by g.id limit 1""")
                .params(exerciseSlug, form, form)
                .query((rs, n) -> new Gloss(rs.getString("en"), rs.getString("az"))).optional()
                .filter(g -> g.en() != null || g.az() != null);
    }

    @Override
    public Optional<String> title(String exerciseSlug) {
        return jdbc.sql("select title from exercise where slug = ?").param(exerciseSlug).query(String.class).optional();
    }
}
