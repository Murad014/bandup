package com.bandup.api.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;

/**
 * The catalog API (database requirement 7, design §5.0) against the seeded
 * Wolves exercise plus exercises added per test. Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CatalogApiTest {

    private static final String CATALOG = "/api/v1/exercises";
    private static final String WOLVES = "$.items[?(@.id == 'yellowstone-wolves')]";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;

    @Test
    void anyoneSeesPublishedExercisesAsCards() throws Exception {
        String body = mvc.perform(get(CATALOG).param("lang", "az"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(WOLVES + ".title").value(hasItem("The Return of the Wolves")))
                .andExpect(jsonPath(WOLVES + ".skill").value(hasItem("READING")))
                .andExpect(jsonPath(WOLVES + ".format").value(hasItem("MINI")))
                .andExpect(jsonPath(WOLVES + ".level").value(hasItem("B1")))
                .andExpect(jsonPath(WOLVES + ".durationSeconds").value(hasItem(300)))
                .andExpect(jsonPath(WOLVES + ".questionCount").value(hasItem(6)))
                .andExpect(jsonPath(WOLVES + ".types[*]").value(contains("TFNG", "FILL")))
                .andExpect(jsonPath(WOLVES + ".topics[0].slug").value(hasItem("nature")))
                .andExpect(jsonPath(WOLVES + ".topics[0].name").value(hasItem("Təbiət")))
                .andExpect(jsonPath(WOLVES + ".done").value(hasItem(false)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("riverbanks", "songbirds", "explanation", "answer", "passageHtml");
        mvc.perform(get(CATALOG).param("lang", "en")).andExpect(jsonPath(WOLVES + ".topics[0].name").value(hasItem("Nature")));
    }

    @Test
    void draftsAndArchivedExercisesAreNotListed() throws Exception {
        exercise("a-draft", "DRAFT", "B1", 300, "TFNG", "nature");
        exercise("archived-one", "ARCHIVED", "B1", 300, "TFNG", "nature");

        mvc.perform(get(CATALOG))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem("a-draft"))))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem("archived-one"))));
    }

    @Test
    void filtersCombineWithAndAndValuesWithOr() throws Exception {
        exercise("science-b2", "PUBLISHED", "B2", 1200, "MCQ", "science");
        exercise("untimed-c1", "PUBLISHED", "C1", null, "MATCH_HEADINGS", "history");

        assertIds(get(CATALOG).param("level", "B1"), "yellowstone-wolves");
        assertIds(get(CATALOG).param("level", "B1").param("level", "B2"), "yellowstone-wolves", "science-b2");
        assertIds(get(CATALOG).param("type", "MCQ"), "science-b2");
        assertIds(get(CATALOG).param("type", "TFNG").param("type", "MCQ"), "yellowstone-wolves", "science-b2");
        assertIds(get(CATALOG).param("topic", "nature"), "yellowstone-wolves");
        assertIds(get(CATALOG).param("topic", "history").param("level", "C1"), "untimed-c1");
        assertIds(get(CATALOG).param("topic", "history").param("level", "B1"));
        // 5 minutes: Wolves (300 s) fits, the 20-minute one doesn't, the untimed one always does.
        assertIds(get(CATALOG).param("maxMinutes", "5"), "yellowstone-wolves", "untimed-c1");
        assertIds(get(CATALOG).param("maxMinutes", "4"), "untimed-c1");
        assertIds(get(CATALOG).param("format", "FULL"));
        assertIds(get(CATALOG).param("skill", "READING").param("format", "MINI").param("level", "B2"), "science-b2");
    }

    @Test
    void pagesAreCountedAndOrderedNewestFirst() throws Exception {
        for (int i = 1; i <= 3; i++) {
            exercise("paged-" + i, "PUBLISHED", "A2", 60, "TFNG", "society");
        }

        mvc.perform(get(CATALOG).param("level", "A2").param("size", "2"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].id").value(contains("paged-3", "paged-2")));
        mvc.perform(get(CATALOG).param("level", "A2").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].id").value(contains("paged-1")));
    }

    @Test
    void aSignedInLearnerSeesTheirBestScoreAndCanHideWhatTheyDid() throws Exception {
        UUID learner = jdbc.queryForObject("insert into app_user (email, ui_language, support_language) values (?, 'az', 'az')"
                + " returning public_id", UUID.class, "learner-" + UUID.randomUUID() + "@bandup.test");
        long userId = jdbc.queryForObject("select id from app_user where public_id = ?", Long.class, learner);
        long wolves = jdbc.queryForObject("select id from exercise where slug = 'yellowstone-wolves'", Long.class);
        jdbc.update("insert into attempt (user_id, exercise_id, exercise_revision, support_language, elapsed_seconds, score, total)"
                + " values (?, ?, 1, 'az', 100, 2, 6), (?, ?, 1, 'az', 90, 5, 6)", userId, wolves, userId, wolves);

        mvc.perform(get(CATALOG).header("Authorization", bearer(learner)))
                .andExpect(jsonPath(WOLVES + ".done").value(hasItem(true)))
                .andExpect(jsonPath(WOLVES + ".bestScore.score").value(hasItem(5)))
                .andExpect(jsonPath(WOLVES + ".bestScore.total").value(hasItem(6)))
                // The token's support language (az) names the topics.
                .andExpect(jsonPath(WOLVES + ".topics[0].name").value(hasItem("Təbiət")));
        mvc.perform(get(CATALOG).param("notDone", "true").header("Authorization", bearer(learner)))
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem("yellowstone-wolves"))));
        // Anonymous: no progress, and notDone is ignored.
        mvc.perform(get(CATALOG).param("notDone", "true"))
                .andExpect(jsonPath(WOLVES + ".done").value(hasItem(false)))
                .andExpect(jsonPath(WOLVES + ".bestScore").value(hasItem((Object) null)));
    }

    @Test
    void malformedFiltersAre400WithEveryProblem() throws Exception {
        mvc.perform(get(CATALOG).param("level", "Z9").param("format", "HUGE").param("size", "500")
                        .param("maxMinutes", "0").param("type", "drop table").param("topic", "Bad Topic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("level", "format", "size", "maxMinutes",
                        "type", "topic")));
    }

    @Test
    void theFilterChoicesAreOnlyWhatPublishedExercisesHave() throws Exception {
        exercise("hidden-draft", "DRAFT", "C2", 60, "MCMA", "technology");

        mvc.perform(get(CATALOG + "/filters").param("lang", "az"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills").value(contains("READING")))
                .andExpect(jsonPath("$.levels").value(hasItem("B1")))
                .andExpect(jsonPath("$.levels").value(not(hasItem("C2"))))
                .andExpect(jsonPath("$.types").value(hasItem("TFNG")))
                .andExpect(jsonPath("$.types").value(not(hasItem("MCMA"))))
                .andExpect(jsonPath("$.topics[?(@.slug == 'nature')].name").value(hasItem("Təbiət")))
                .andExpect(jsonPath("$.topics[*].slug").value(not(hasItem("technology"))));
    }

    // ------------------------------------------------------------ helpers

    private void assertIds(MockHttpServletRequestBuilder request, String... ids) throws Exception {
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder((Object[]) ids)));
    }

    /** A one-question exercise; later calls are published later (newest first). */
    private void exercise(String slug, String status, String level, Integer seconds, String type, String topic) {
        long id = jdbc.queryForObject("""
                insert into exercise (slug, skill, target_language, title, format, level, duration_seconds, status, published_at)
                values (?, 'READING', 'en', ?, 'MINI', ?, ?, ?, now() + (select count(*) from exercise) * interval '1 second')
                returning id""", Long.class, slug, "Exercise " + slug, level, seconds, status);
        long group = jdbc.queryForObject("""
                insert into question_group (exercise_id, position, type_code, instruction_html) values (?, 1, ?, 'Answer.')
                returning id""", Long.class, id, type);
        jdbc.update("insert into question (exercise_id, group_id, position, stem_text) values (?, ?, 1, 'A question.')", id, group);
        jdbc.update("insert into exercise_topic (exercise_id, topic_id) select ?, id from topic where slug = ?", id, topic);
    }

    private String bearer(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(userId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles", List.of("LEARNER")).claim("lang", "az").build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
