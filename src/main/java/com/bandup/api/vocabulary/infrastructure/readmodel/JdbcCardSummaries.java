package com.bandup.api.vocabulary.infrastructure.readmodel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.CardSummaries;
import com.bandup.api.vocabulary.domain.word.valueobject.WordId;

/** {@link CardSummaries} with SQL over {@code vocab_word}, its forms and saves. */
@Component
@RequiredArgsConstructor
class JdbcCardSummaries implements CardSummaries {

    private static final String FILTER = """
            where (cast(:status as varchar) is null or w.status = :status)
              and (cast(:query as varchar) is null
                   or exists (select 1 from vocab_word_form f where f.word_id = w.id and f.form like :like))
            """;

    private final JdbcClient jdbc;

    @Override
    public List<CardSummary> list(String status, String query, int page, int size) {
        return jdbc.sql("""
                select w.id, coalesce(w.lemma, (select min(f.form) from vocab_word_form f where f.word_id = w.id)) as word,
                       w.content ->> 'level' as level, w.status, w.source, w.last_error, w.updated_at,
                       (select count(*) from vocab_saved_word s where s.word_id = w.id) as saves
                from vocab_word w
                """ + FILTER + " order by w.updated_at desc, w.id desc limit :size offset :offset")
                .params(params(status, query)).param("size", size).param("offset", page * size)
                .query((rs, n) -> new CardSummary(rs.getLong("id"), rs.getString("word"), rs.getString("level"),
                        rs.getString("status"), rs.getString("source"), rs.getInt("saves"), rs.getString("last_error"),
                        rs.getTimestamp("updated_at").toInstant()))
                .list();
    }

    @Override
    public long total(String status, String query) {
        return jdbc.sql("select count(*) from vocab_word w " + FILTER).params(params(status, query)).query(Long.class)
                .single();
    }

    @Override
    public int saves(WordId card) {
        return jdbc.sql("select count(*) from vocab_saved_word where word_id = ?").param(card.value())
                .query(Integer.class).single();
    }

    private static Map<String, Object> params(String status, String query) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", status);
        params.put("query", query);
        params.put("like", query == null ? "" : "%" + query.replace("%", "").replace("_", "") + "%");
        return params;
    }
}
