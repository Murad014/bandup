package com.bandup.api.practice.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/** "My progress" (backend requirement 11): a learner's own attempts — summary, list, review. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ProgressApiTest {

    private static final String ALL_RIGHT = """
            [{"n":1,"value":"FALSE"},{"n":2,"value":"TRUE"},{"n":3,"value":"NOT GIVEN"},
             {"n":4,"value":"riverbanks"},{"n":5,"value":"songbirds"},{"n":6,"value":"wolves"}]""";
    private static final String HALF_RIGHT = """
            [{"n":1,"value":"TRUE"},{"n":2,"value":"TRUE"},{"n":3,"value":"NOT GIVEN"},
             {"n":4,"value":"riverbanks"},{"n":5,"value":"cats"}]""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;

    private String aysel;
    private String other;
    private String firstAttempt;
    private String secondAttempt;
    private String anonymousAttempt;
    private String othersAttempt;

    @BeforeEach
    void twoLearnersAndSomeAttempts() throws Exception {
        aysel = token(newUser(), "az");
        other = token(newUser(), "en");
        firstAttempt = submit(ALL_RIGHT, 40, aysel);
        secondAttempt = submit(HALF_RIGHT, 60, aysel);
        anonymousAttempt = submit(ALL_RIGHT, 10, null);
        othersAttempt = submit(ALL_RIGHT, 20, other);
        // One transaction: give the attempts distinct times (Aysel's second is the newest).
        jdbc.update("update attempt set submitted_at = now() - interval '2 days' where id = ?::uuid", firstAttempt);
        jdbc.update("update attempt set submitted_at = now() - interval '1 day' where id = ?::uuid", secondAttempt);
    }

    @Test
    void theSummaryCountsOnlyTheLearnersOwnAttempts() throws Exception {
        mvc.perform(get("/api/v1/me/progress").header("Authorization", aysel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(2))
                .andExpect(jsonPath("$.passagesDone").value(1))
                .andExpect(jsonPath("$.totalSeconds").value(100))
                .andExpect(jsonPath("$.averagePercent").value(75))   // 100 % and 50 %
                .andExpect(jsonPath("$.bestPercent").value(100))
                .andExpect(jsonPath("$.passages[0].slug").value("yellowstone-wolves"))
                .andExpect(jsonPath("$.passages[0].title").value("The Return of the Wolves"));

        String newcomer = token(newUser(), "en");
        mvc.perform(get("/api/v1/me/progress").header("Authorization", newcomer))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.averagePercent").isEmpty())
                .andExpect(jsonPath("$.passages", hasSize(0)));
    }

    @Test
    void theListIsNewestFirstInPagesAndCanBeFiltered() throws Exception {
        mvc.perform(get("/api/v1/me/attempts").header("Authorization", aysel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].attemptId", contains(secondAttempt, firstAttempt)))
                .andExpect(jsonPath("$.items[0].passageId").value("yellowstone-wolves"))
                .andExpect(jsonPath("$.items[0].title").value("The Return of the Wolves"))
                .andExpect(jsonPath("$.items[0].score").value(3))
                .andExpect(jsonPath("$.items[0].total").value(6))
                .andExpect(jsonPath("$.items[0].percent").value(50))
                .andExpect(jsonPath("$.items[0].elapsedSeconds").value(60))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[0].coverUrl").isEmpty())
                .andExpect(jsonPath("$.items[0].topics[0]").value("nature"))
                .andExpect(jsonPath("$.items[0].submittedAt").exists());

        mvc.perform(get("/api/v1/me/attempts").param("page", "1").param("size", "1").header("Authorization", aysel))
                .andExpect(jsonPath("$.items[*].attemptId", contains(firstAttempt)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/me/attempts").param("passageId", "some-other-passage").header("Authorization", aysel))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/me/attempts").param("size", "51").param("page", "-1").header("Authorization", aysel))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("page", "size")));
    }

    @Test
    void aReviewShowsTheStoredAnswersWithExplanationsInTheHelpLanguage() throws Exception {
        mvc.perform(get("/api/v1/me/attempts/" + secondAttempt).header("Authorization", aysel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passageId").value("yellowstone-wolves"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.updatedSince").value(false))
                .andExpect(jsonPath("$.result.attemptId").value(secondAttempt))
                .andExpect(jsonPath("$.result.score").value(3))
                .andExpect(jsonPath("$.result.percent").value(50))
                .andExpect(jsonPath("$.result.elapsedSeconds").value(60))
                .andExpect(jsonPath("$.result.results", hasSize(6)))
                .andExpect(jsonPath("$.result.results[0].n").value(1))
                .andExpect(jsonPath("$.result.results[0].correct").value(false))
                .andExpect(jsonPath("$.result.results[0].given").value("TRUE"))
                .andExpect(jsonPath("$.result.results[0].answer").value("FALSE"))
                .andExpect(jsonPath("$.result.results[0].explanation").exists())
                .andExpect(jsonPath("$.result.results[0].evidenceId").exists())
                .andExpect(jsonPath("$.result.results[3].correct").value(true))
                .andExpect(jsonPath("$.result.results[5].given").value(""));   // not answered

        // Explanations follow the help language; the stored grade doesn't change when the exercise does.
        jdbc.update("""
                insert into question_explanation (question_id, language_code, explanation_html)
                select question_id, 'az', 'Mətndə əsas səbəb ovdur.' from attempt_answer
                where attempt_id = ?::uuid and question_number = 1""", secondAttempt);
        org.assertj.core.api.Assertions.assertThat(text("/api/v1/me/attempts/" + secondAttempt + "?lang=az", "explanation"))
                .isEqualTo("Mətndə əsas səbəb ovdur.");
        org.assertj.core.api.Assertions.assertThat(text("/api/v1/me/attempts/" + secondAttempt + "?lang=en", "explanation"))
                .startsWith("<b>Mainly</b>");

        jdbc.update("update exercise set revision = revision + 1 where slug = 'yellowstone-wolves'");
        jdbc.update("update question_answer set value = 'river banks' where value = 'riverbanks'");
        mvc.perform(get("/api/v1/me/attempts/" + secondAttempt).header("Authorization", aysel))
                .andExpect(jsonPath("$.updatedSince").value(true))
                .andExpect(jsonPath("$.result.results[3].correct").value(true))
                .andExpect(jsonPath("$.result.results[3].answer").value("riverbanks"));

        jdbc.update("update exercise set status = 'DRAFT' where slug = 'yellowstone-wolves'");
        mvc.perform(get("/api/v1/me/attempts").header("Authorization", aysel))
                .andExpect(jsonPath("$.items[0].available").value(false));
    }

    @Test
    void nobodyElsesAttemptCanBeSeen() throws Exception {
        for (String id : List.of(othersAttempt, anonymousAttempt, UUID.randomUUID().toString(), "not-an-id")) {
            mvc.perform(get("/api/v1/me/attempts/" + id).header("Authorization", aysel))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/me/attempts").header("Authorization", other))
                .andExpect(jsonPath("$.items[*].attemptId", contains(othersAttempt)));
        for (String path : List.of("/api/v1/me/progress", "/api/v1/me/attempts", "/api/v1/me/attempts/" + firstAttempt)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------ helpers

    private String text(String url, String field) throws Exception {
        Map<?, ?> body = json.readValue(mvc.perform(get(url).header("Authorization", aysel)).andReturn()
                .getResponse().getContentAsString(), Map.class);
        return (String) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) body.get("result")).get("results")).getFirst()).get(field);
    }

    private String submit(String answers, int seconds, String authorization) throws Exception {
        var request = post("/api/v1/attempts").contentType(MediaType.APPLICATION_JSON).content(
                "{\"passageId\":\"yellowstone-wolves\",\"elapsedSeconds\":" + seconds + ",\"answers\":" + answers + "}");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        String body = mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return (String) json.readValue(body, Map.class).get("attemptId");
    }

    private UUID newUser() {
        return jdbc.queryForObject("""
                insert into app_user (email, ui_language, support_language) values (?, 'az', 'az')
                returning public_id""", UUID.class, "learner-" + UUID.randomUUID() + "@example.com");
    }

    private String token(UUID userId, String supportLanguage) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(userId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("roles", List.of("LEARNER")).claim("lang", supportLanguage).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
