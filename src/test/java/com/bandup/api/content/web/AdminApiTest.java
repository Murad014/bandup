package com.bandup.api.content.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The admin API end to end (backend requirements 4, 5, 7): role checks, the
 * full editable object, CRUD, publish rules, versions, revisions, archiving
 * and sanitizing. Each test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminApiTest {

    private static final String ADMIN_API = "/api/v1/admin/passages";
    private static final String WOLVES = ADMIN_API + "/yellowstone-wolves";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;

    // ------------------------------------------------------------ access

    @Test
    void onlyAdminsAndAuthorsGetIn() throws Exception {
        mvc.perform(get(ADMIN_API)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_API).header("Authorization", bearer("LEARNER"))).andExpect(status().isForbidden());
        mvc.perform(post(WOLVES + "/unpublish").header("Authorization", bearer("LEARNER"))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_API).header("Authorization", bearer("AUTHOR"))).andExpect(status().isOk());
        mvc.perform(get(ADMIN_API).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ read

    @Test
    void listsExercisesWithTheirState() throws Exception {
        mvc.perform(as(get(ADMIN_API)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'yellowstone-wolves')].status").value(hasItem("PUBLISHED")))
                .andExpect(jsonPath("$[?(@.id == 'yellowstone-wolves')].questionCount").value(hasItem(6)))
                .andExpect(jsonPath("$[?(@.id == 'yellowstone-wolves')].stageTitle").value(hasItem("Stage 1")))
                .andExpect(jsonPath("$[?(@.id == 'yellowstone-wolves')].position").value(hasItem(4)));
    }

    @Test
    void theFullEditableObjectIncludesAnswersAndEveryLanguage() throws Exception {
        mvc.perform(as(get(WOLVES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("yellowstone-wolves"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.durationSeconds").value(300))
                .andExpect(jsonPath("$.glosses.g1.surface").value("eliminated"))
                .andExpect(jsonPath("$.glosses.g1.meanings.az.text").value("məhv edilmək"))
                .andExpect(jsonPath("$.glosses.g1.meanings.en.source").value("MACHINE"))
                .andExpect(jsonPath("$.questionGroups[0].heading").value("Questions 1–3"))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].n").value(1))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].id").isNumber())
                .andExpect(jsonPath("$.questionGroups[0].questions[0].answer[0]").value("FALSE"))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].evidenceId").value("e1"))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].explanations.en.text").value(containsString("hunting")))
                .andExpect(jsonPath("$.questionGroups[1].maxWords").value(1))
                .andExpect(jsonPath("$.questionGroups[1].questions[0].pre").value(containsString("trees near")));
        mvc.perform(as(get(ADMIN_API + "/no-such-thing"))).andExpect(status().isNotFound());
        mvc.perform(as(get(ADMIN_API + "/Not A Slug"))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ write

    @Test
    void createUpdateDeleteRoundTrip() throws Exception {
        // A draft may be incomplete: no answers yet.
        JsonNode created = body(mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(newExercise()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("elk-and-willows"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.glosses.g1.surface").value("willows"))
                .andExpect(jsonPath("$.glosses.g1.lemma").value("willow"))
                .andExpect(jsonPath("$.glosses.g1.meanings.az.text").value("söyüd"))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].answer", hasSize(0))));
        assertThat(load("elk-and-willows")).isEqualTo(created);

        // Update with the loaded version: answers filled in, a question added.
        ObjectNode edit = (ObjectNode) created.deepCopy();
        ArrayNode questions = (ArrayNode) edit.get("questionGroups").get(0).get("questions");
        ((ObjectNode) questions.get(0)).putArray("answer").add("TRUE");
        questions.addObject().put("text", "Beavers left the park.").put("evidenceId", "e2").putArray("answer").add("FALSE");
        JsonNode updated = body(save("elk-and-willows", edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.questionGroups[0].questions", hasSize(2)))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].id").value(created.at("/questionGroups/0/questions/0/id").asLong()))
                .andExpect(jsonPath("$.questionGroups[0].questions[1].n").value(2)));
        assertThat(load("elk-and-willows")).isEqualTo(updated);

        // The old version is now stale.
        save("elk-and-willows", edit).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("changed by someone else")));

        mvc.perform(as(delete(ADMIN_API + "/elk-and-willows"))).andExpect(status().isNoContent());
        mvc.perform(as(get(ADMIN_API + "/elk-and-willows"))).andExpect(status().isNotFound());
    }

    @Test
    void authorHtmlIsSanitizedBeforeItIsStored() throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(newExercise());
        input.put("passageHtml", "<p onclick=\"steal()\"><span class=\"ev\" id=\"e1\">Elk ate the "
                + "<span class=\"w\" data-g=\"g1\" style=\"x\">willows</span><script>alert(1)</script></span></p>"
                + "<span class=\"ev\" id=\"e2\">Beavers came back.</span>");
        ((ObjectNode) input.at("/questionGroups/0")).put("instruction", "Choose <strong>TRUE</strong><img src=x onerror=alert(1)>");
        ((ObjectNode) input.at("/questionGroups/0/questions/0")).putObject("explanations").putObject("en")
                .put("text", "<b>Yes</b><span class=\"ev\" id=\"e7\">.</span><iframe></iframe>");

        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passageHtml").value("<p><span class=\"ev\" id=\"e1\">Elk ate the "
                        + "<span class=\"w\" data-g=\"g1\">willows</span></span></p><span class=\"ev\" id=\"e2\">Beavers came back.</span>"))
                .andExpect(jsonPath("$.questionGroups[0].instruction").value("Choose <strong>TRUE</strong>"))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].explanations.en.text").value("<b>Yes</b><span>.</span>"));
        assertThat(jdbc.queryForObject("select body_html from reading_passage rp join exercise x on x.id = rp.exercise_id "
                + "where x.slug = 'elk-and-willows'", String.class)).doesNotContain("script", "onclick", "style");
    }

    @Test
    void publishIsBlockedWithFieldErrorsAndNothingChanges() throws Exception {
        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(newExercise())).andExpect(status().isCreated());

        mvc.perform(as(post(ADMIN_API + "/elk-and-willows/publish")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("questionGroups[0].questions[0].answer")))
                .andExpect(jsonPath("$.errors[0].message").value("Q1 must be one of TRUE / FALSE / NOT GIVEN."));
        mvc.perform(as(get(ADMIN_API + "/elk-and-willows"))).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/v1/passages/elk-and-willows")).andExpect(status().isNotFound());
    }

    @Test
    void publishShowsItToLearnersAndUnpublishHidesIt() throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(newExercise());
        ((ObjectNode) input.at("/questionGroups/0/questions/0")).putArray("answer").add("TRUE");
        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(input.toString())).andExpect(status().isCreated());

        mvc.perform(as(post(ADMIN_API + "/elk-and-willows/publish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/passages/elk-and-willows").param("lang", "az"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.glosses.g1").value("söyüd"));
        mvc.perform(post("/api/v1/attempts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passageId\":\"elk-and-willows\",\"elapsedSeconds\":5,\"answers\":[{\"n\":1,\"value\":\"true\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(1));

        mvc.perform(as(post(ADMIN_API + "/elk-and-willows/unpublish"))).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/v1/passages/elk-and-willows")).andExpect(status().isNotFound());
    }

    @Test
    void savingAPublishedExerciseRaisesItsRevisionAndKeepsItPublishable() throws Exception {
        JsonNode wolves = load("yellowstone-wolves");
        ObjectNode edit = (ObjectNode) wolves.deepCopy();
        edit.put("title", "The Return of the Grey Wolves");

        save("yellowstone-wolves", edit).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        ObjectNode broken = (ObjectNode) load("yellowstone-wolves").deepCopy();
        ((ObjectNode) broken.at("/questionGroups/0/questions/0")).putArray("answer");
        save("yellowstone-wolves", broken).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("questionGroups[0].questions[0].answer"));
        assertThat(load("yellowstone-wolves").get("revision").asInt()).isEqualTo(2);
    }

    @Test
    void deletingAnExerciseWithAttemptsArchivesIt() throws Exception {
        mvc.perform(post("/api/v1/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"passageId\":\"yellowstone-wolves\",\"elapsedSeconds\":5,\"answers\":[]}")).andExpect(status().isCreated());

        mvc.perform(as(delete(WOLVES))).andExpect(status().isNoContent());

        mvc.perform(as(get(WOLVES))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/passages/yellowstone-wolves")).andExpect(status().isNotFound());
        mvc.perform(as(post(WOLVES + "/publish"))).andExpect(status().isUnprocessableContent());
        save("yellowstone-wolves", load("yellowstone-wolves")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void aQuestionLearnersAnsweredCantBeRemoved() throws Exception {
        mvc.perform(post("/api/v1/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"passageId\":\"yellowstone-wolves\",\"elapsedSeconds\":5,\"answers\":[]}")).andExpect(status().isCreated());
        ObjectNode edit = (ObjectNode) load("yellowstone-wolves").deepCopy();
        ((ArrayNode) edit.at("/questionGroups/1/questions")).remove(2);

        save("yellowstone-wolves", edit).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("questionGroups"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Q6 can't be removed: learners have answered it")));
    }

    @Test
    void referencesAndKeysAreCheckedWithClearFieldErrors() throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(newExercise());
        input.put("stageId", 999_999).put("position", 1).put("level", "Z9");
        input.putArray("topicIds").add(424_242);
        ((ObjectNode) input.at("/glosses/g1/meanings")).putObject("xx").put("text", "?");
        ((ObjectNode) input.at("/questionGroups/0")).put("type", "ESSAY");
        ObjectNode question = (ObjectNode) input.at("/questionGroups/0/questions/0");
        question.put("id", 101).put("evidenceId", "ev9");
        question.putArray("options").addObject().put("key", "A B").put("label", "bad key");

        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("level", "stageId", "topicIds",
                        "glosses.g1.meanings.xx", "questionGroups[0].type", "questionGroups[0].questions[0].id",
                        "questionGroups[0].questions[0].evidenceId", "questionGroups[0].questions[0].options[0].key")));

        ObjectNode duplicate = (ObjectNode) json.readTree(newExercise());
        duplicate.put("id", "yellowstone-wolves");
        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(duplicate.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("id"));

        ObjectNode badSlug = (ObjectNode) json.readTree(newExercise());
        badSlug.put("id", "Bad Slug!");
        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content(badSlug.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void passageKeyRulesReachTheAuthor() throws Exception {
        ObjectNode edit = (ObjectNode) load("yellowstone-wolves").deepCopy();
        String html = edit.get("passageHtml").asString()
                .replace("data-g=\"g2\"", "data-g=\"x2\"")       // invalid gloss key
                .replace("data-g=\"g3\"", "data-g=\"g1\"")       // used twice
                .replace("<span class=\"ev\" id=\"e4\">", "<span>"); // evidence still used by Q4
        edit.put("passageHtml", html);

        save("yellowstone-wolves", edit).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder(
                        "Gloss key 'x2' must be 'g' followed by digits.",
                        "Gloss key 'g1' is used twice in the passage.",
                        "Q4 points to evidence 'e4', which isn't in the passage.")));
    }

    @Test
    void updateNeedsTheVersion() throws Exception {
        ObjectNode edit = (ObjectNode) load("yellowstone-wolves").deepCopy();
        edit.remove("version");

        save("yellowstone-wolves", edit).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void theResponseNeverLeaksSqlOrStackTraces() throws Exception {
        mvc.perform(as(post(ADMIN_API)).contentType(MediaType.APPLICATION_JSON).content("{\"id\": 5, \"questionGroups\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(not(containsString("Exception"))));
    }

    // ------------------------------------------------------------ helpers

    /** A small READING draft: one TFNG group with one question that has no answer yet. */
    private String newExercise() {
        return """
                {"id": "elk-and-willows", "title": "Elk and Willows", "format": "MINI", "level": "B1",
                 "durationSeconds": 120, "lowTimeThresholdSeconds": 30, "topicIds": [],
                 "passageHtml": "<p><span class=\\"ev\\" id=\\"e1\\">Elk ate the <span class=\\"w\\" data-g=\\"g1\\">willows</span>.</span> <span class=\\"ev\\" id=\\"e2\\">Beavers came back.</span></p>",
                 "glosses": {"g1": {"lemma": "willow", "meanings": {"az": {"text": "söyüd"}}}},
                 "questionGroups": [
                   {"type": "TFNG", "instruction": "Do the statements agree with the text?",
                    "questions": [{"text": "Elk ate willows.", "evidenceId": "e1", "answer": []}]}
                 ]}""";
    }

    private org.springframework.test.web.servlet.ResultActions save(String slug, JsonNode body) throws Exception {
        return mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(ADMIN_API + "/" + slug))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private JsonNode load(String slug) throws Exception {
        return body(mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ADMIN_API + "/" + slug)))
                .andExpect(status().isOk()));
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
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
