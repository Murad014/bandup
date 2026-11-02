package com.bandup.api.grammar.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The admin grammar API end to end (grammar requirement 9): roles, import of
 * the lesson format, every error at once, sanitizing, versions, revisions,
 * publish positions, archiving and dropping review cards. Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminGrammarApiTest {

    private static final String API = "/api/v1/admin/grammar/lessons";
    private static final String SLUG = "b1-test-present-perfect";
    private static final String LESSON = API + "/" + SLUG;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;

    // ------------------------------------------------------------- access

    @Test
    void onlyAdminsAndAuthorsGetIn() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).header("Authorization", bearer("LEARNER"))).andExpect(status().isForbidden());
        mvc.perform(post(API).header("Authorization", bearer("LEARNER")).contentType(MediaType.APPLICATION_JSON)
                .content(lesson().toString())).andExpect(status().isForbidden());
        mvc.perform(get(API).header("Authorization", bearer("AUTHOR"))).andExpect(status().isOk());
        mvc.perform(get(API).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------- create

    @Test
    void importsALessonAsADraftWithEveryTextAsALanguageObject() throws Exception {
        JsonNode created = body(create(lesson()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SLUG))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.position").doesNotExist())
                .andExpect(jsonPath("$.level").value("B1"))
                .andExpect(jsonPath("$.title.en").value("Present Perfect vs Past Simple"))
                .andExpect(jsonPath("$.goal.az").value("Sonda present perfect və past simple arasında seçim edəcəksiniz."))
                .andExpect(jsonPath("$.learn[0].body.en").value("<b>have/has + past participle</b>. The connection to now matters."))
                .andExpect(jsonPath("$.learn[2].steps[0].en").value("1️⃣ Is there a <b>finished-time word</b>? → <b>Past simple</b>"))
                .andExpect(jsonPath("$.learn[1].tip").doesNotExist())
                .andExpect(jsonPath("$.worked.answer").value("visited"))
                .andExpect(jsonPath("$.practice[0].answer").value(1))
                .andExpect(jsonPath("$.practice[0].whys[0].en").value("“lost” is finished past, but the result matters now"))
                .andExpect(jsonPath("$.check[2].accept[1]").value("have not seen"))
                .andExpect(jsonPath("$.check[3].wrong").value("I have seen him yesterday."))
                .andExpect(jsonPath("$.check[4].tiles[0]").value("since"))
                .andExpect(jsonPath("$.check[4].answer").value("We have known him since 2019"))
                .andExpect(jsonPath("$.check[4].explanation.en").value("“since” → present perfect.")));
        assertThat(body(mvc.perform(as(get(LESSON))).andExpect(status().isOk()))).isEqualTo(created);
    }

    @Test
    void reportsEveryFormatProblemAtOnceWithItsJsonPath() throws Exception {
        ObjectNode bad = lesson();
        bad.remove("title");
        bad.put("minutes", 90);
        ((ArrayNode) bad.get("learn")).remove(0);
        ((ArrayNode) bad.get("learn")).remove(0);
        ((ObjectNode) bad.get("check").get(0)).put("answer", 7);
        ((ObjectNode) bad.get("check").get(1)).put("sentence", "No gap here.");
        ((ObjectNode) bad.get("check").get(4)).putArray("tiles").add("We").add("have");
        create(bad).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("title", "minutes", "learn",
                        "check[0].answer", "check[1].sentence", "check[4].answer")));
        assertThat(count()).isZero();
    }

    @Test
    void unreadableShapesAreReportedFirst() throws Exception {
        ObjectNode bad = lesson();
        bad.put("level", "C2");
        ((ObjectNode) bad.get("check").get(0)).put("type", "essay");
        ((ObjectNode) bad.get("check").get(1)).put("answer", "won"); // ignored for "type"
        ((ObjectNode) bad.get("practice").get(0)).put("answer", "have lost");
        bad.set("goal", json.createArrayNode().add("not a text"));
        ((ObjectNode) bad.get("topic")).set("label", json.createObjectNode().put("xx", "Unknown"));
        create(bad).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("level", "check[0].type",
                        "practice[0].answer", "goal", "topic.label.xx")));
    }

    @Test
    void theIdMustBeValidAndFree() throws Exception {
        create(lesson().put("id", "Bad Id")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
        create(lesson()).andExpect(status().isCreated());
        create(lesson()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    void textsAreSanitizedButKeepBoldItalicLineBreaksAndSymbols() throws Exception {
        ObjectNode lesson = lesson();
        lesson.put("title", "Tenses <script>alert(1)</script><a href=\"x\" onclick=\"y\">now</a> <b>bold</b> <i class=\"z\">it</i><br>→ “ok” 🧭");
        create(lesson).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title.en").value("Tenses now <b>bold</b> <i>it</i><br>→ “ok” 🧭"));
    }

    // ------------------------------------------------------------- update

    @Test
    void anUpdateNeedsTheCurrentVersionAndAPublishedEditRaisesTheRevision() throws Exception {
        JsonNode created = body(create(lesson()));
        ObjectNode edit = (ObjectNode) created.deepCopy();
        edit.remove("version");
        update(edit).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("version"));
        edit.put("version", 5);
        update(edit).andExpect(status().isConflict());

        edit.put("version", 0);
        edit.set("title", json.createObjectNode().put("en", "Present Perfect or Past Simple?"));
        update(edit).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.title.en").value("Present Perfect or Past Simple?"));

        publish(null).andExpect(status().isOk());
        edit.put("version", 2);
        update(edit).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void removingACheckItemDropsItsReviewCards() throws Exception {
        JsonNode created = body(create(lesson()));
        long lessonId = jdbc.queryForObject("select id from grammar_lesson where slug = ?", Long.class, SLUG);
        long user = newUser();
        for (String item : List.of("c2", "c5")) {
            jdbc.update("insert into grammar_review_card (id, user_id, lesson_id, item_id, box, due_on) values (?, ?, ?, ?, 1, current_date)",
                    UUID.randomUUID(), user, lessonId, item);
        }
        ObjectNode edit = (ObjectNode) created.deepCopy();
        ArrayNode check = (ArrayNode) edit.get("check");
        check.remove(4); // c5
        check.addObject().put("id", "c6").put("type", "type").put("prompt", "Type the verb:")
                .put("sentence", "She ___ just left.").put("explanation", "“just” → present perfect.")
                .putArray("accept").add("has");
        update(edit).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select item_id from grammar_review_card where lesson_id = ?", String.class, lessonId))
                .containsExactly("c2");
    }

    // ------------------------------------------------------------ publish

    @Test
    void publishingWithoutAPositionPutsTheLessonAtTheEndOfItsCourse() throws Exception {
        Integer last = jdbc.queryForObject(
                "select max(position) from grammar_lesson where level = 'B1' and status = 'PUBLISHED'", Integer.class);
        int end = last == null ? 1 : last + 1;
        create(lesson());
        publish(null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.position").value(end)).andExpect(jsonPath("$.publishedAt").isNotEmpty());

        create(lesson().put("id", "b1-test-second"));
        mvc.perform(as(post(API + "/b1-test-second/publish"))).andExpect(jsonPath("$.position").value(end + 1));
    }

    @Test
    void aChosenPositionIsUsedAndTwoLessonsCantShareIt() throws Exception {
        create(lesson());
        publish("{\"position\": 900}").andExpect(status().isOk()).andExpect(jsonPath("$.position").value(900));
        create(lesson().put("id", "b1-test-second").put("position", 900));
        mvc.perform(as(post(API + "/b1-test-second/publish"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Position 900 is already used in the B1 course."));
        publish("{\"position\": 900}", "b1-test-second").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Position 900 is already used in the B1 course."));
    }

    @Test
    void anEditCantMoveAPublishedLessonToATakenPosition() throws Exception {
        create(lesson());
        JsonNode published = body(publish("{\"position\": 900}"));
        create(lesson().put("id", "b1-test-second"));
        publish("{\"position\": 901}", "b1-test-second").andExpect(status().isOk());
        ObjectNode edit = (ObjectNode) published.deepCopy();
        edit.put("position", 901);
        update(edit).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Position 901 is already used in the B1 course."));
    }

    @Test
    void aPositionStartsAtOne() throws Exception {
        create(lesson());
        publish("{\"position\": 0}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("position"));
    }

    @Test
    void unpublishTakesTheLessonOutOfTheCourse() throws Exception {
        create(lesson());
        publish(null);
        mvc.perform(as(post(LESSON + "/unpublish"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ------------------------------------------------------------- delete

    @Test
    void anUnusedLessonIsDeletedAUsedOneArchived() throws Exception {
        create(lesson());
        mvc.perform(as(delete(LESSON))).andExpect(status().isNoContent());
        mvc.perform(as(get(LESSON))).andExpect(status().isNotFound());

        create(lesson());
        long lessonId = jdbc.queryForObject("select id from grammar_lesson where slug = ?", Long.class, SLUG);
        jdbc.update("insert into grammar_run (id, lesson_id, lesson_revision, started_at) values (?, ?, 1, now())",
                UUID.randomUUID(), lessonId);
        mvc.perform(as(delete(LESSON))).andExpect(status().isNoContent());
        mvc.perform(as(get(LESSON))).andExpect(jsonPath("$.status").value("ARCHIVED"));
        ObjectNode edit = (ObjectNode) body(mvc.perform(as(get(LESSON))));
        update(edit).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    // --------------------------------------------------------------- list

    @Test
    void listsLessonsFilteredByLevelAndStatus() throws Exception {
        create(lesson());
        create(lesson().put("id", "c1-test-lesson").put("level", "C1"));
        mvc.perform(as(get(API).param("level", "B1")))
                .andExpect(jsonPath("$[*].id").value(hasItem(SLUG)))
                .andExpect(jsonPath("$[*].id").value(not(hasItem("c1-test-lesson"))))
                .andExpect(jsonPath("$[?(@.id == '" + SLUG + "')].checkCount").value(hasItem(5)))
                .andExpect(jsonPath("$[?(@.id == '" + SLUG + "')].topic").value(hasItem("present-perfect")))
                .andExpect(jsonPath("$[?(@.id == '" + SLUG + "')].title").value(hasItem("Present Perfect vs Past Simple")));
        mvc.perform(as(get(API).param("status", "PUBLISHED"))).andExpect(jsonPath("$[*].id").value(not(hasItem(SLUG))));
    }

    @Test
    void unknownOrMalformedIdsAreNotFound() throws Exception {
        mvc.perform(as(get(API + "/no-such-lesson"))).andExpect(status().isNotFound());
        mvc.perform(as(get(API + "/Not A Slug"))).andExpect(status().isNotFound());
        mvc.perform(as(post(API + "/no-such-lesson/publish"))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- helpers

    private ObjectNode lesson() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/grammar/b1-lesson.json")) {
            return (ObjectNode) json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private ResultActions create(JsonNode lesson) throws Exception {
        return mvc.perform(as(post(API)).contentType(MediaType.APPLICATION_JSON).content(lesson.toString()));
    }

    private ResultActions update(JsonNode lesson) throws Exception {
        return mvc.perform(as(put(LESSON)).contentType(MediaType.APPLICATION_JSON).content(lesson.toString()));
    }

    private ResultActions publish(String body) throws Exception {
        return publish(body, null);
    }

    private ResultActions publish(String body, String slug) throws Exception {
        MockHttpServletRequestBuilder request = as(post((slug == null ? LESSON : API + "/" + slug) + "/publish"));
        return mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from grammar_lesson where slug = ?", Integer.class, SLUG);
    }

    private long newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                id, id + "@example.com");
        return jdbc.queryForObject("select id from app_user where public_id = ?", Long.class, id);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer("AUTHOR"));
    }

    private String bearer(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles", List.of(role)).claim("lang", "en").build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
