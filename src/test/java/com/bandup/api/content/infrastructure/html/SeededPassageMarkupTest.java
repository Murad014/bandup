package com.bandup.api.content.infrastructure.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;

/** The seeded Wolves passage already fits the allow-list, and its keys are read correctly. */
@JdbcTest
@Import(TestcontainersConfiguration.class)
class SeededPassageMarkupTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void theSeededPassagePassesThroughUnchangedAndYieldsItsKeys() {
        String html = jdbc.queryForObject("""
                select body_html from reading_passage
                where exercise_id = (select id from exercise where slug = 'yellowstone-wolves')""", String.class);

        String sanitized = new JsoupHtmlSanitizer().passage(html).html();
        MarkupKeys keys = new JsoupMarkupInspector().inspect(new JsoupHtmlSanitizer().passage(html));

        assertThat(sanitized).isEqualTo(html);
        assertThat(keys.glosses()).extracting(MarkupKeys.GlossMark::key)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 25).mapToObj(i -> "g" + i).toList());
        assertThat(keys.glosses().getFirst().surface()).isEqualTo("eliminated");
        assertThat(keys.glosses()).extracting(MarkupKeys.GlossMark::surface)
                .containsExactlyElementsOf(jdbc.queryForList("""
                        select surface from passage_gloss
                        where exercise_id = (select id from exercise where slug = 'yellowstone-wolves')
                        order by substring(gloss_key from 2)::int""", String.class));
        // Document order, as in the passage: e4 comes before e2.
        assertThat(keys.evidence()).containsExactly("e1", "e4", "e2", "e5", "e6");
        assertThat(keys.evidence()).containsExactlyInAnyOrderElementsOf(jdbc.queryForList("""
                select evidence_key from passage_evidence
                where exercise_id = (select id from exercise where slug = 'yellowstone-wolves')""", String.class));
    }
}
