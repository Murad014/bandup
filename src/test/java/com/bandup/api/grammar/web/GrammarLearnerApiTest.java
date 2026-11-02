package com.bandup.api.grammar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.grammar.application.RunCleanup;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The learner grammar API end to end (grammar requirements 1–8): course and
 * unlocking, a full lesson run graded on the server, finishing, the daily
 * limit, the review deck, ownership and errors. B2 is cleared at the start of
 * each test, so only this test's lessons are in that course. Rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GrammarLearnerApiTest {

    private static final String API = "/api/v1/grammar";
    private static final String ADMIN = "/api/v1/admin/grammar/lessons";
    private static final String ONE = "b2-test-one", TWO = "b2-test-two", THREE = "b2-test-three";
    /** The right answers of the test lesson's check items. */
    private static final List<Object[]> RIGHT = List.of(
            new Object[] {"c1", 1}, new Object[] {"c2", "won"}, new Object[] {"c3", "have not seen"},
            new Object[] {"c4", "saw"}, new Object[] {"c5", List.of("We", "have", "known", "him", "since", "2019")});

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired RunCleanup cleanup;

    private String anna;

    @BeforeEach
    void aB2CourseOfThreeLessons() throws Exception {
        jdbc.update("update grammar_lesson set status = 'DRAFT' where level = 'B2'");
        publish(ONE, 1, "present-perfect");
        publish(TWO, 2, "present-perfect");
        publish(THREE, 3, "conditionals");
        anna = newLearner();
    }

    // -------------------------------------------------------------- levels

    @Test
    void levelsListEveryLevelWithItsPublishedLessons() throws Exception {
        mvc.perform(get(API + "/levels")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].level").value(containsInAnyOrder("A2", "B1", "B2", "C1")))
                .andExpect(jsonPath("$[?(@.level == 'B2')].lessonCount").value(hasItem(3)))
                .andExpect(jsonPath("$[?(@.level == 'C1')].name").value(hasItem("Advanced")));
    }

    @Test
    void aSignedInLearnerKeepsTheirLevel() throws Exception {
        mvc.perform(as(anna, get(API + "/me"))).andExpect(jsonPath("$.level").doesNotExist());
        mvc.perform(as(anna, put(API + "/me/level")).contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"C1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(as(anna, get(API + "/me"))).andExpect(jsonPath("$.level").value("C1"));
        mvc.perform(as(anna, put(API + "/me/level")).contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"C2\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("level"));
        mvc.perform(get(API + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(put(API + "/me/level").contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"B1\"}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------- course

    @Test
    void guestsMayOpenOnlyTheFirstLesson() throws Exception {
        mvc.perform(get(API + "/course").param("level", "B2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(3))
                .andExpect(jsonPath("$.finished").value(0))
                .andExpect(jsonPath("$.next.slug").value(ONE))
                .andExpect(jsonPath("$.review").doesNotExist())
                .andExpect(jsonPath("$.lessons[*].state").value(contains("NEXT", "LOCKED", "LOCKED")))
                .andExpect(jsonPath("$.lessons[1].lockedReason").value("SIGN_IN"))
                .andExpect(jsonPath("$.lessons[0].title").value("Present Perfect vs Past Simple"))
                .andExpect(jsonPath("$.lessons[0].minutes").value(20))
                .andExpect(jsonPath("$.topics[*].key").value(contains("present-perfect", "conditionals")));
        mvc.perform(get(API + "/lessons/" + ONE)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("NEXT"));
        mvc.perform(get(API + "/lessons/" + TWO)).andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/lessons/" + TWO + "/runs")).andExpect(status().isUnauthorized());
    }

    @Test
    void theCourseIsPagedAndFilteredByTopic() throws Exception {
        mvc.perform(get(API + "/course").param("level", "B2").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.lessons", hasSize(1)))
                .andExpect(jsonPath("$.lessons[0].slug").value(THREE)).andExpect(jsonPath("$.page").value(1));
        mvc.perform(get(API + "/course").param("level", "B2").param("topic", "conditionals"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.lessons[0].slug").value(THREE))
                .andExpect(jsonPath("$.available").value(3));
        mvc.perform(get(API + "/course").param("level", "X9")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void textsComeInTheAskedLanguageWithEnglishAsFallback() throws Exception {
        mvc.perform(get(API + "/lessons/" + ONE).param("lang", "az"))
                .andExpect(jsonPath("$.goal").value("Sonda present perfect və past simple arasında seçim edəcəksiniz."))
                .andExpect(jsonPath("$.title").value("Present Perfect vs Past Simple"));
    }

    // ---------------------------------------------------------- a full run

    @Test
    void aLessonAndItsRunCarryNoAnswerKeys() throws Exception {
        String lesson = text(mvc.perform(get(API + "/lessons/" + ONE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.worked.answer").value("visited"))
                .andExpect(jsonPath("$.learn", hasSize(3))).andExpect(jsonPath("$.checkCount").value(5)));
        assertThat(lesson).doesNotContain("\"check\"", "\"accept\"", "\"whys\"", "\"explanation\"");

        JsonNode run = body(startRun(null).andExpect(status().isCreated()));
        assertThat(run.toString()).doesNotContain("accept", "whys", "explanation", "\"answer\"", "\"show\"");
        assertThat(run.get("check").get(0).get("options")).hasSize(4);
        JsonNode tiles = run.get("check").get(4).get("tiles");
        assertThat(tiles).hasSize(6);
        assertThat(String.join(" ", tiles.valueStream().map(JsonNode::asString).toList()))
                .isNotEqualTo("We have known him since 2019");
    }

    @Test
    void eachRunShufflesTheTilesItsOwnWay() throws Exception {
        List<String> stored = List.of("since", "We", "him", "have", "2019", "known");
        List<List<String>> orders = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orders.add(body(startRun(null)).get("check").get(4).get("tiles").valueStream().map(JsonNode::asString).toList());
        }
        assertThat(orders).as("not just the stored order").anyMatch(order -> !order.equals(stored));
    }

    @Test
    void aGuestRunIsGradedOnTheServerAndFinishesWhenEveryCheckItemIsRight() throws Exception {
        String run = body(startRun(null)).get("runId").asString();
        answer(null, run, "p1", 0).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.expected").value("have lost"))
                .andExpect(jsonPath("$.why").value("“lost” is finished past, but the result matters now"))
                .andExpect(jsonPath("$.explanation").value("Result matters now → present perfect."))
                .andExpect(jsonPath("$.retries").value(0));
        answer(null, run, "c2", "wins").andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.expected").value("won")).andExpect(jsonPath("$.retries").value(1))
                .andExpect(jsonPath("$.mastered").value(0)).andExpect(jsonPath("$.total").value(5));
        for (Object[] right : RIGHT) {
            answer(null, run, (String) right[0], right[1]).andExpect(jsonPath("$.correct").value(true));
        }
        answer(null, run, "c1", 1).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select finished_at is not null from grammar_run where id = ?::uuid",
                Boolean.class, run)).isTrue();
    }

    @Test
    void wrongKindsOfAnswersAndUnknownItemsAreRefused() throws Exception {
        String run = body(startRun(null)).get("runId").asString();
        answer(null, run, "c1", "have been").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("answer"));
        answer(null, run, "c5", List.of("We", "have")).andExpect(status().isUnprocessableContent());
        answer(null, run, "c9", "x").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("itemId"));
        answer(null, run, "c2", json.createObjectNode()).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aRunIsOnlyItsLearnersAndAGuestRunOnlyByItsId() throws Exception {
        String annasRun = body(startRun(anna)).get("runId").asString();
        String bob = newLearner();
        answer(bob, annasRun, "c1", 1).andExpect(status().isNotFound());
        answer(null, annasRun, "c1", 1).andExpect(status().isNotFound());
        answer(null, UUID.randomUUID().toString(), "c1", 1).andExpect(status().isNotFound());
        answer(anna, annasRun, "c1", 1).andExpect(status().isOk());
    }

    @Test
    void aLessonEditedDuringARunMakesTheRunStartAgain() throws Exception {
        String run = body(startRun(null)).get("runId").asString();
        String author = bearer(UUID.randomUUID().toString(), "AUTHOR");
        ObjectNode lesson = (ObjectNode) body(mvc.perform(get(ADMIN + "/" + ONE).header("Authorization", author)));
        lesson.set("title", json.createObjectNode().put("en", "Present Perfect, again"));
        mvc.perform(put(ADMIN + "/" + ONE).header("Authorization", author).contentType(MediaType.APPLICATION_JSON)
                .content(lesson.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        answer(null, run, "c1", 1).andExpect(status().isConflict());
    }

    // ------------------------------------------- finishing and the daily limit

    @Test
    void finishingALessonSavesItFillsTheDeckAndLocksTheNextUntilTomorrow() throws Exception {
        finishLesson(anna, ONE);
        mvc.perform(as(anna, get(API + "/course").param("level", "B2")))
                .andExpect(jsonPath("$.finished").value(1))
                .andExpect(jsonPath("$.next.slug").value(TWO))
                .andExpect(jsonPath("$.lessons[*].state").value(contains("DONE", "LOCKED", "LOCKED")))
                .andExpect(jsonPath("$.lessons[1].lockedReason").value("TOMORROW"))
                .andExpect(jsonPath("$.lessons[2].lockedReason").value("FINISH_PREVIOUS"))
                .andExpect(jsonPath("$.review.due").value(0))
                .andExpect(jsonPath("$.review.nextDueOn").value(today().plusDays(1).toString()));
        mvc.perform(as(anna, get(API + "/lessons/" + TWO))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOMORROW"));
        mvc.perform(as(anna, get(API + "/lessons/" + THREE))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FINISH_PREVIOUS"));
        assertThat(cardsOf(anna)).isEqualTo(5);

        // The day after, the next lesson opens.
        jdbc.update("update grammar_finished_lesson set first_finished_on = first_finished_on - 1");
        mvc.perform(as(anna, get(API + "/course").param("level", "B2")))
                .andExpect(jsonPath("$.lessons[*].state").value(contains("DONE", "NEXT", "LOCKED")));
        mvc.perform(as(anna, get(API + "/lessons/" + TWO))).andExpect(status().isOk());
    }

    @Test
    void aReplayIsAlwaysAllowedAndAddsNoDuplicateCards() throws Exception {
        finishLesson(anna, ONE);
        mvc.perform(as(anna, get(API + "/lessons/" + ONE))).andExpect(jsonPath("$.state").value("DONE"));
        finishLesson(anna, ONE);
        assertThat(cardsOf(anna)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select times_finished from grammar_finished_lesson f join app_user u on u.id = f.user_id where u.public_id = ?::uuid",
                Integer.class, anna)).isEqualTo(2);
    }

    @Test
    void aGuestFinishingSavesNothing() throws Exception {
        long before = jdbc.queryForObject("select count(*) from grammar_review_card", Long.class);
        finishLesson(null, ONE);
        assertThat(jdbc.queryForObject("select count(*) from grammar_review_card", Long.class)).isEqualTo(before);
    }

    // -------------------------------------------------------------- review

    @Test
    void dueCardsComeWithoutKeysAndEachDayOnlyTheFirstAnswerMovesACard() throws Exception {
        finishLesson(anna, ONE);
        mvc.perform(as(anna, get(API + "/review"))).andExpect(jsonPath("$.cards", hasSize(0)))
                .andExpect(jsonPath("$.dueTotal").value(0));
        jdbc.update("update grammar_review_card set due_on = ?", java.sql.Date.valueOf(today()));

        JsonNode due = body(mvc.perform(as(anna, get(API + "/review"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueTotal").value(5)).andExpect(jsonPath("$.cards", hasSize(5)))
                .andExpect(jsonPath("$.cards[0].topic.key").value("present-perfect"))
                .andExpect(jsonPath("$.cards[0].lessonTitle").value("Present Perfect vs Past Simple")));
        assertThat(due.toString()).doesNotContain("accept", "whys", "explanation", "\"show\"");
        String c2 = cardFor(due, "c2");

        reviewAnswer(anna, c2, "wins").andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.expected").value("won")).andExpect(jsonPath("$.box").value(1))
                .andExpect(jsonPath("$.nextDueOn").value(today().plusDays(1).toString()))
                .andExpect(jsonPath("$.changed").value(true));
        reviewAnswer(anna, c2, "won").andExpect(jsonPath("$.correct").value(true)).andExpect(jsonPath("$.box").value(1))
                .andExpect(jsonPath("$.changed").value(false));
        String c1 = cardFor(due, "c1");
        reviewAnswer(anna, c1, 1).andExpect(jsonPath("$.box").value(2))
                .andExpect(jsonPath("$.nextDueOn").value(today().plusDays(3).toString()));
        mvc.perform(as(anna, get(API + "/course").param("level", "B2"))).andExpect(jsonPath("$.review.due").value(3));
    }

    @Test
    void theReviewNeedsATokenAndCardsBelongToTheirLearner() throws Exception {
        finishLesson(anna, ONE);
        jdbc.update("update grammar_review_card set due_on = ?", java.sql.Date.valueOf(today()));
        String card = body(mvc.perform(as(anna, get(API + "/review")))).get("cards").get(0).get("cardId").asString();
        mvc.perform(get(API + "/review")).andExpect(status().isUnauthorized());
        reviewAnswer(null, card, 1).andExpect(status().isUnauthorized());
        reviewAnswer(newLearner(), card, 1).andExpect(status().isNotFound());
    }

    @Test
    void cardsOfLessonsTakenOutOfTheCourseAreNotShown() throws Exception {
        finishLesson(anna, ONE);
        jdbc.update("update grammar_review_card set due_on = ?", java.sql.Date.valueOf(today()));
        jdbc.update("update grammar_lesson set status = 'DRAFT' where slug = ?", ONE);
        mvc.perform(as(anna, get(API + "/review"))).andExpect(jsonPath("$.cards", hasSize(0)));
    }

    // -------------------------------------------------------------- errors

    @Test
    void draftUnknownAndMalformedLessonsAreNotFound() throws Exception {
        mvc.perform(post(ADMIN + "/" + ONE + "/unpublish").header("Authorization",
                bearer(UUID.randomUUID().toString(), "AUTHOR"))).andExpect(status().isOk());
        mvc.perform(get(API + "/lessons/" + ONE)).andExpect(status().isNotFound());
        mvc.perform(get(API + "/lessons/no-such-lesson")).andExpect(status().isNotFound());
        mvc.perform(get(API + "/lessons/NOT-A-SLUG")).andExpect(status().isNotFound());
        mvc.perform(get(API + "/course").param("level", "B2"))
                .andExpect(jsonPath("$.lessons[*].slug").value(not(hasItem(ONE))));
    }

    @Test
    void theCleanupDeletesOldUnfinishedRuns() throws Exception {
        String run = body(startRun(null)).get("runId").asString();
        jdbc.update("update grammar_run set started_at = now() - interval '8 days' where id = ?::uuid", run);
        assertThat(cleanup.purge()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from grammar_run where id = ?::uuid", Integer.class, run)).isZero();
    }

    // ------------------------------------------------------------- helpers

    private void publish(String slug, int position, String topic) throws Exception {
        ObjectNode lesson;
        try (InputStream in = getClass().getResourceAsStream("/grammar/b1-lesson.json")) {
            lesson = (ObjectNode) json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        lesson.put("id", slug).put("level", "B2");
        ((ObjectNode) lesson.get("topic")).put("key", topic);
        mvc.perform(post(ADMIN).header("Authorization", bearer(UUID.randomUUID().toString(), "AUTHOR"))
                .contentType(MediaType.APPLICATION_JSON).content(lesson.toString())).andExpect(status().isCreated());
        mvc.perform(post(ADMIN + "/" + slug + "/publish").header("Authorization", bearer(UUID.randomUUID().toString(), "AUTHOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"position\":" + position + "}"))
                .andExpect(status().isOk());
    }

    private void finishLesson(String learner, String slug) throws Exception {
        String run = body(mvc.perform(as(learner, post(API + "/lessons/" + slug + "/runs"))).andExpect(status().isCreated()))
                .get("runId").asString();
        for (Object[] right : RIGHT) {
            answer(learner, run, (String) right[0], right[1]).andExpect(jsonPath("$.correct").value(true));
        }
    }

    private ResultActions startRun(String learner) throws Exception {
        return mvc.perform(as(learner, post(API + "/lessons/" + ONE + "/runs")));
    }

    private ResultActions answer(String learner, String run, String item, Object answer) throws Exception {
        ObjectNode body = json.createObjectNode().put("itemId", item);
        body.set("answer", json.valueToTree(answer));
        return mvc.perform(as(learner, post(API + "/runs/" + run + "/answers")).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private ResultActions reviewAnswer(String learner, String card, Object answer) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.set("answer", json.valueToTree(answer));
        return mvc.perform(as(learner, post(API + "/review/" + card + "/answer")).contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private static String cardFor(JsonNode due, String item) {
        for (JsonNode card : due.get("cards")) {
            if (card.get("item").get("id").asString().equals(item)) {
                return card.get("cardId").asString();
            }
        }
        throw new AssertionError("No card for " + item);
    }

    private int cardsOf(String learner) {
        return jdbc.queryForObject("""
                select count(*) from grammar_review_card c join app_user u on u.id = c.user_id where u.public_id = ?::uuid""",
                Integer.class, learner);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Baku"));
    }

    private String newLearner() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                id, id + "@example.com");
        return id.toString();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(text(result));
    }

    private static String text(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The request as {@code learner} (a public id), or as a guest when null. */
    private MockHttpServletRequestBuilder as(String learner, MockHttpServletRequestBuilder request) {
        return learner == null ? request : request.header("Authorization", bearer(learner, "LEARNER"));
    }

    private String bearer(String subject, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles", List.of(role)).claim("lang", "en").build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
