package com.bandup.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;

/**
 * Checks the V2 seed: reference data is complete, and the Yellowstone exercise
 * is a faithful conversion of static/js/reading/fixtures.js (data-az glosses
 * turned into data-g keys + translation rows).
 */
@JdbcTest
@Import(TestcontainersConfiguration.class)
class SeedDataTest {

    private static final String WOLVES = "(select id from exercise where slug = 'yellowstone-wolves')";

    @Autowired
    JdbcTemplate jdbc;

    // -------------------------------------------------------------- languages

    @Test
    void enablesEnglishAndAzerbaijaniOnly() {
        assertThat(jdbc.queryForList("select code from language where enabled order by code", String.class))
                .containsExactly("az", "en");
        assertThat(jdbc.queryForObject("select direction from language where code = 'ar'", String.class)).isEqualTo("rtl");
    }

    @Test
    void everyFallbackChainEndsAtEnglish() {
        List<String> notEndingAtEnglish = jdbc.queryForList("""
                with recursive chain(start, code, depth) as (
                    select code, code, 0 from language
                    union all
                    select c.start, l.fallback_code, c.depth + 1
                    from chain c join language l on l.code = c.code
                    where l.fallback_code is not null and c.depth < 10
                )
                select start from chain group by start
                having bool_or(code = 'en') = false or max(depth) >= 10
                """, String.class);
        assertThat(notEndingAtEnglish).isEmpty();
    }

    // --------------------------------------------------------- question types

    @Test
    void questionTypesMatchTheAdminCatalog() throws IOException {
        Map<String, String> jsFamilies = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(\\w+):\\s*\\{\\s*label:[^}]*family:\\s*\"(\\w+)\"")
                .matcher(classpathText("static/js/admin/questionTypes.js"));
        while (m.find()) {
            jsFamilies.put(m.group(1), m.group(2));
        }
        Map<String, String> dbFamilies = new LinkedHashMap<>();
        jdbc.query("select code, family from question_type", rs -> {
            dbFamilies.put(rs.getString("code"), rs.getString("family"));
        });

        assertThat(jsFamilies).hasSize(11);
        assertThat(dbFamilies).containsAllEntriesOf(jsFamilies);
        // Added by the database design; the admin catalog gets them in tasks.md task 8.
        assertThat(dbFamilies.keySet()).hasSize(13).contains("SUMMARY_BOX", "DIAGRAM_LABEL");
        assertThat(dbFamilies).containsEntry("SUMMARY_BOX", "match").containsEntry("DIAGRAM_LABEL", "gap");
    }

    // ------------------------------------------------------------- yellowstone

    @Test
    void wolvesIsAPublishedMiniReadingAboutNature() {
        Map<String, Object> ex = jdbc.queryForMap(
                "select status, skill, format, level, duration_seconds, low_time_threshold_seconds, position, published_at from exercise where slug = 'yellowstone-wolves'");
        assertThat(ex).containsEntry("status", "PUBLISHED").containsEntry("skill", "READING")
                .containsEntry("format", "MINI").containsEntry("level", "B1")
                .containsEntry("duration_seconds", 300).containsEntry("low_time_threshold_seconds", 60)
                .containsEntry("position", 4);
        assertThat(ex.get("published_at")).isNotNull();
        assertThat(jdbc.queryForList(
                "select t.slug from exercise_topic et join topic t on t.id = et.topic_id where et.exercise_id = " + WOLVES,
                String.class)).containsExactly("nature");
        assertThat(jdbc.queryForObject("""
                select st.title from exercise e join stage_translation st on st.stage_id = e.stage_id and st.language_code = 'az'
                where e.slug = 'yellowstone-wolves'
                """, String.class)).isEqualTo("Mərhələ 1");
    }

    @Test
    void glossesAreAFaithfulConversionOfTheFixture() throws IOException {
        // Ordered (surface, meaning) pairs as authored in the old fixture.
        List<List<String>> fixture = new ArrayList<>();
        Matcher m = Pattern.compile("<span class=\"w\" data-az=\"([^\"]*)\">([^<]*)</span>")
                .matcher(classpathText("static/js/reading/fixtures.js"));
        while (m.find()) {
            fixture.add(List.of(m.group(2), m.group(1)));
        }

        List<List<String>> seeded = jdbc.query("""
                select g.surface, t.meaning from passage_gloss g
                join passage_gloss_translation t on t.gloss_id = g.id and t.language_code = 'az'
                where g.exercise_id = %s
                order by substring(g.gloss_key from 2)::int
                """.formatted(WOLVES), (rs, i) -> List.of(rs.getString(1), rs.getString(2)));

        assertThat(fixture).hasSize(25);
        assertThat(seeded).isEqualTo(fixture);
    }

    @Test
    void everyWolvesGlossHasAnEnglishDefinitionAwaitingReview() {
        Map<String, Object> english = jdbc.queryForMap("""
                select count(*) as total,
                       count(*) filter (where t.source = 'MACHINE' and not t.reviewed) as unreviewed_machine
                from passage_gloss g
                join passage_gloss_translation t on t.gloss_id = g.id and t.language_code = 'en'
                where g.exercise_id = %s
                """.formatted(WOLVES));
        assertThat(english).containsEntry("total", 25L).containsEntry("unreviewed_machine", 25L);
        assertThat(jdbc.queryForObject("""
                select t.meaning from passage_gloss g
                join passage_gloss_translation t on t.gloss_id = g.id and t.language_code = 'en'
                where g.gloss_key = 'g4' and g.exercise_id = %s
                """.formatted(WOLVES), String.class)).isEqualTo("a very large kind of deer");
    }

    @Test
    void passageHtmlAndGlossEvidenceRowsAgree() {
        String html = jdbc.queryForObject("select body_html from reading_passage where exercise_id = " + WOLVES, String.class);
        assertThat(html).doesNotContain("data-az");

        assertThat(keysIn(html, "data-g=\"(g\\d+)\"")).containsExactlyInAnyOrderElementsOf(
                jdbc.queryForList("select gloss_key from passage_gloss where exercise_id = " + WOLVES, String.class));
        assertThat(keysIn(html, "<span class=\"ev\" id=\"(e\\d+)\">")).containsExactlyInAnyOrderElementsOf(
                jdbc.queryForList("select evidence_key from passage_evidence where exercise_id = " + WOLVES, String.class));
    }

    @Test
    void questionsHaveAnswersEvidenceAndExplanationsInOrder() {
        // Derived numbering: group position, then question position.
        List<String> rows = jdbc.query("""
                select g.type_code, coalesce(q.stem_text, q.stem_pre) as stem, ev.evidence_key,
                       (select string_agg(a.value, '|' order by a.position) from question_answer a where a.question_id = q.id) as answers,
                       (select count(*) from question_explanation x where x.question_id = q.id and x.language_code = 'en') as en_expl
                from question q
                join question_group g on g.id = q.group_id
                left join passage_evidence ev on ev.id = q.evidence_id
                where q.exercise_id = %s
                order by g.position, q.position
                """.formatted(WOLVES),
                (rs, i) -> (i + 1) + " " + rs.getString("type_code") + " " + rs.getString("evidence_key") + " "
                        + rs.getString("answers") + " expl=" + rs.getInt("en_expl"));

        assertThat(rows).containsExactly(
                "1 TFNG e1 FALSE expl=1",
                "2 TFNG e2 TRUE expl=1",
                "3 TFNG e2 NOT GIVEN expl=1",
                "4 FILL e4 riverbanks expl=1",
                "5 FILL e5 songbirds expl=1",
                "6 FILL e6 wolves expl=1");
    }

    @Test
    void fillGroupHasTheOneWordLimitFromItsInstruction() {
        Map<String, Object> fill = jdbc.queryForMap(
                "select instruction_html, max_words, numbers_allowed from question_group where type_code = 'FILL' and exercise_id = " + WOLVES);
        assertThat((String) fill.get("instruction_html")).contains("ONE WORD ONLY");
        assertThat(fill).containsEntry("max_words", 1).containsEntry("numbers_allowed", false);
    }

    // ---------------------------------------------------------------- helpers

    private static String classpathText(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> keysIn(String html, String regex) {
        List<String> keys = new ArrayList<>();
        Matcher m = Pattern.compile(regex).matcher(html);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }
}
