package com.bandup.api.practice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/**
 * The learner API end to end against the seeded Wolves exercise (backend
 * requirements 2, 3, 6, 7). Each test runs in a transaction that is rolled
 * back, so content edits and stored attempts don't leak between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PracticeApiTest {

    private static final String WOLVES = "/api/v1/passages/yellowstone-wolves";
    private static final String ANSWERS = """
            [{"n":1,"value":"FALSE"},{"n":2,"value":"TRUE"},{"n":3,"value":"NOT GIVEN"},
             {"n":4,"value":"riverbanks"},{"n":5,"value":"songbirds"},{"n":6,"value":"wolves"}]""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;

    // ------------------------------------------------------------ passage

    @Test
    void wolvesPassageInAzerbaijani() throws Exception {
        mvc.perform(get(WOLVES).param("lang", "az"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("yellowstone-wolves"))
                .andExpect(jsonPath("$.language").value("az"))
                .andExpect(jsonPath("$.direction").value("ltr"))
                .andExpect(jsonPath("$.stageLabel").value("Mərhələ 1"))
                .andExpect(jsonPath("$.position").value(4))
                .andExpect(jsonPath("$.title").value("The Return of the Wolves"))
                .andExpect(jsonPath("$.durationSeconds").value(300))
                .andExpect(jsonPath("$.lowTimeThresholdSeconds").value(60))
                .andExpect(jsonPath("$.passageHtml").value(containsString("data-g=\"g1\"")))
                .andExpect(jsonPath("$.glosses.g25").value("aid etmək, hesabına yazmaq"))
                .andExpect(jsonPath("$.glosses.length()").value(25))
                .andExpect(jsonPath("$.questionGroups", hasSize(2)))
                .andExpect(jsonPath("$.questionGroups[0].type").value("TFNG"))
                .andExpect(jsonPath("$.questionGroups[0].heading").value("Questions 1–3"))
                .andExpect(jsonPath("$.questionGroups[0].instruction").value(containsString("<strong>TRUE</strong>")))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].n").value(1))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].type").value("TFNG"))
                .andExpect(jsonPath("$.questionGroups[0].questions[0].text").value(containsString("mainly because of disease")))
                .andExpect(jsonPath("$.questionGroups[1].heading").value("Questions 4–6"))
                .andExpect(jsonPath("$.questionGroups[1].maxWords").value(1))
                .andExpect(jsonPath("$.questionGroups[1].numbersAllowed").value(false))
                .andExpect(jsonPath("$.questionGroups[1].questions[0].pre").value(containsString("trees near")))
                .andExpect(jsonPath("$.questionGroups[1].questions[0].post").value("."))
                .andExpect(jsonPath("$.questionGroups[1].questions[0].text").doesNotExist());
    }

    @Test
    void thePassageLeaksNoAnswersExplanationsOrEvidenceLinks() throws Exception {
        String body = mvc.perform(get(WOLVES).param("lang", "az")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The passage text itself holds the gap answers and the ev spans (that's the exam);
        // nothing else may reveal an answer, an explanation or which span proves what.
        String withoutPassage = body.replaceAll("\"passageHtml\":\"(\\\\.|[^\"\\\\])*\"", "");
        assertThat(withoutPassage).contains("\"glosses\"").doesNotContain("passageHtml");
        assertThat(withoutPassage).doesNotContain("answer", "explanation", "evidence", "riverbanks", "songbirds",
                "Mainly", "hunting", "\"e1\"", "\"e4\"");
    }

    @Test
    void helpLanguageComesFromParamThenTokenThenBrowserAndFallsBackPerItem() throws Exception {
        // Browser asks for Turkish: nothing stored in Turkish, every item falls back to English.
        mvc.perform(get(WOLVES).header("Accept-Language", "tr-TR,tr;q=0.9"))
                .andExpect(jsonPath("$.language").value("tr"))
                .andExpect(jsonPath("$.stageLabel").value("Stage 1"))
                .andExpect(jsonPath("$.glosses.g1").value("removed completely"));

        // The signed-in learner's support language wins over the browser.
        mvc.perform(get(WOLVES).header("Accept-Language", "tr").header("Authorization", "Bearer " + token(UUID.randomUUID(), "az")))
                .andExpect(jsonPath("$.language").value("az"));

        // ?lang= wins over both; a regional code uses its base language.
        mvc.perform(get(WOLVES).param("lang", "az-AZ").header("Authorization", "Bearer " + token(UUID.randomUUID(), "tr")))
                .andExpect(jsonPath("$.language").value("az"));

        // One gloss without an Azerbaijani meaning: only that one is English.
        jdbc.update("""
                delete from passage_gloss_translation where language_code = 'az'
                  and gloss_id = (select id from passage_gloss where gloss_key = 'g1')""");
        mvc.perform(get(WOLVES).param("lang", "az"))
                .andExpect(jsonPath("$.glosses.g1").value("removed completely"))
                .andExpect(jsonPath("$.glosses.g25").value("aid etmək, hesabına yazmaq"));

        // No language asked for at all → English.
        mvc.perform(get(WOLVES)).andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    void draftAndUnknownPassagesAre404() throws Exception {
        mvc.perform(get("/api/v1/passages/no-such-passage"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        jdbc.update("update exercise set status = 'DRAFT' where slug = 'yellowstone-wolves'");
        mvc.perform(get(WOLVES)).andExpect(status().isNotFound());
        mvc.perform(submit("""
                {"passageId":"yellowstone-wolves","elapsedSeconds":10,"answers":[]}""")).andExpect(status().isNotFound());
    }

    @Test
    void anExpiredOrForgedTokenIsRejectedEvenOnPublicEndpoints() throws Exception {
        mvc.perform(get(WOLVES).header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ attempts

    @Test
    void aPerfectAttempt() throws Exception {
        mvc.perform(submit(attempt(214, ANSWERS)).param("lang", "en"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(6))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(jsonPath("$.elapsedSeconds").value(214))
                .andExpect(jsonPath("$.attemptId").isNotEmpty())
                .andExpect(jsonPath("$.results", hasSize(6)))
                .andExpect(jsonPath("$.results[0].n").value(1))
                .andExpect(jsonPath("$.results[0].correct").value(true))
                .andExpect(jsonPath("$.results[0].given").value("FALSE"))
                .andExpect(jsonPath("$.results[0].answer").value("FALSE"))
                .andExpect(jsonPath("$.results[0].explanation").value(containsString("<b>hunting</b>")))
                .andExpect(jsonPath("$.results[0].evidenceId").value("e1"))
                .andExpect(jsonPath("$.results[3].answer").value("riverbanks"));
    }

    @Test
    void aMixedAttemptWithBlankAndWrongAnswers() throws Exception {
        mvc.perform(submit(attempt(95, """
                [{"n":1,"value":"true"},{"n":4,"value":"  RIVERBANKS "},{"n":5,"value":"song birds"},{"n":6,"value":null}]""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.percent").value(17))
                .andExpect(jsonPath("$.results[0].correct").value(false))
                .andExpect(jsonPath("$.results[0].given").value("true"))
                .andExpect(jsonPath("$.results[1].given").value(""))
                .andExpect(jsonPath("$.results[3].correct").value(true))
                .andExpect(jsonPath("$.results[3].given").value("  RIVERBANKS "))
                .andExpect(jsonPath("$.results[4].correct").value(false))
                .andExpect(jsonPath("$.results[5].given").value(""));
    }

    @Test
    void chooseTwoEarnsPartialPointsAndTakesTwoNumbers() throws Exception {
        addChooseTwoQuestion();

        mvc.perform(get(WOLVES))
                .andExpect(jsonPath("$.questionGroups[2].heading").value("Questions 7–8"))
                .andExpect(jsonPath("$.questionGroups[2].chooseCount").value(2))
                .andExpect(jsonPath("$.questionGroups[2].questions[0].n").value(7))
                .andExpect(jsonPath("$.questionGroups[2].questions[0].nTo").value(8))
                .andExpect(jsonPath("$.questionGroups[2].questions[0].options", hasSize(4)));

        mvc.perform(submit(attempt(60, """
                [{"n":7,"value":["A","C"]}]""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.results[6].n").value(7))
                .andExpect(jsonPath("$.results[6].nTo").value(8))
                .andExpect(jsonPath("$.results[6].points").value(1))
                .andExpect(jsonPath("$.results[6].possible").value(2))
                .andExpect(jsonPath("$.results[6].correct").value(false))
                .andExpect(jsonPath("$.results[6].given", containsInAnyOrder("A", "C")))
                .andExpect(jsonPath("$.results[6].answer").value("A, D"))
                .andExpect(jsonPath("$.results[6].acceptedAnswers", containsInAnyOrder("A", "D")));

        // Three letters for "Choose TWO" score nothing; answering under number 8 works too.
        mvc.perform(submit(attempt(60, """
                [{"n":8,"value":["A","C","D"]}]""")))
                .andExpect(jsonPath("$.results[6].points").value(0));
    }

    @Test
    void explanationsFollowTheHelpLanguageWithFallback() throws Exception {
        jdbc.update("""
                insert into question_explanation (question_id, language_code, explanation_html)
                select q.id, 'az', 'Azərbaycanca izah' from question q
                join question_group g on g.id = q.group_id
                where g.exercise_id = (select id from exercise where slug = 'yellowstone-wolves')
                  and g.position = 1 and q.position = 1""");

        mvc.perform(submit(attempt(10, "[]")).param("lang", "az"))
                .andExpect(jsonPath("$.results[0].explanation").value("Azərbaycanca izah"))
                .andExpect(jsonPath("$.results[1].explanation").value(containsString("17 is more than 14")));
        assertThat(jdbc.queryForObject("select support_language from attempt", String.class)).isEqualTo("az");
    }

    @Test
    void theResultIsStoredAndKeepsItsSnapshotAfterAContentEdit() throws Exception {
        String attemptId = id(mvc.perform(submit(attempt(30, """
                [{"n":4,"value":"riverbanks"}]"""))).andReturn());

        jdbc.update("""
                update question_answer set value = 'river banks'
                where value = 'riverbanks' and question_id in
                  (select id from question where exercise_id = (select id from exercise where slug = 'yellowstone-wolves'))""");
        jdbc.update("update exercise set revision = 2 where slug = 'yellowstone-wolves'");

        Map<String, Object> stored = jdbc.queryForMap("""
                select a.exercise_revision, a.score, a.total, a.elapsed_seconds, a.user_id,
                       aa.expected_value::text as expected, aa.given_value::text as given, aa.is_correct
                from attempt a join attempt_answer aa on aa.attempt_id = a.id
                where a.id = ?::uuid and aa.question_number = 4""", attemptId);
        assertThat(stored).containsEntry("exercise_revision", 1).containsEntry("score", 1).containsEntry("total", 6)
                .containsEntry("elapsed_seconds", 30).containsEntry("expected", "[\"riverbanks\"]")
                .containsEntry("given", "\"riverbanks\"").containsEntry("is_correct", true);
        assertThat(stored.get("user_id")).isNull();

        // The same answer is now wrong for new attempts, which record revision 2.
        mvc.perform(submit(attempt(30, """
                [{"n":4,"value":"riverbanks"}]""")))
                .andExpect(jsonPath("$.results[3].correct").value(false))
                .andExpect(jsonPath("$.results[3].answer").value("river banks"));
        assertThat(jdbc.queryForList("select exercise_revision from attempt order by submitted_at, exercise_revision", Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    void anAttemptWithATokenBelongsToThatLearner() throws Exception {
        UUID aysel = jdbc.queryForObject("""
                insert into app_user (email, ui_language, support_language) values (?, 'az', 'az')
                returning public_id""", UUID.class, "learner-" + UUID.randomUUID() + "@example.com"); // other tests commit users

        String attemptId = id(mvc.perform(submit(attempt(40, ANSWERS)).header("Authorization", "Bearer " + token(aysel, "az")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].explanation").exists())
                .andReturn());

        assertThat(jdbc.queryForObject("""
                select u.public_id from attempt a join app_user u on u.id = a.user_id where a.id = ?::uuid""",
                UUID.class, attemptId)).isEqualTo(aysel);
        assertThat(jdbc.queryForObject("select support_language from attempt where id = ?::uuid", String.class, attemptId))
                .isEqualTo("az");
    }

    @Test
    void malformedSubmissionsAre400WithEveryProblem() throws Exception {
        // Shape problems and exercise problems are reported together.
        mvc.perform(submit(attempt(-5, """
                [{"n":1,"value":"TRUE"},{"n":1,"value":"FALSE"},{"n":99,"value":"x"},{"value":"y"},{"n":0,"value":"z"},
                 {"n":7,"value":"FALSE"}]""")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder(
                        "Question 1 is answered more than once.",
                        "Every answer needs a question number.",
                        "Question number 0 doesn't exist.",
                        "Question number 99 doesn't exist.",
                        "Question number 7 doesn't exist.",
                        "Elapsed time can't be negative.")));

        // Two numbers of one "Choose TWO" question answer it twice.
        addChooseTwoQuestion();
        mvc.perform(submit(attempt(5, """
                [{"n":7,"value":["A"]},{"n":8,"value":["D"]}]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder("Question 8 is answered more than once.")));

        mvc.perform(submit("""
                {"passageId":"yellowstone-wolves","answers":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("elapsedSeconds"));
        mvc.perform(submit("""
                {"elapsedSeconds":5,"answers":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("passageId"));
        mvc.perform(submit(attempt(5, """
                [{"n":1,"value":7}]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("answers[0].value"));
        mvc.perform(submit("{not json")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from attempt", Integer.class)).isZero();
    }

    @Test
    void theClientCantSendAGrade() throws Exception {
        mvc.perform(submit("""
                {"passageId":"yellowstone-wolves","elapsedSeconds":5,"score":6,"total":6,
                 "answers":[{"n":1,"value":"TRUE","correct":true}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.results[0].correct").value(false));
    }

    // ------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(String body) {
        return post("/api/v1/attempts").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String attempt(int elapsedSeconds, String answers) {
        return "{\"passageId\":\"yellowstone-wolves\",\"elapsedSeconds\":" + elapsedSeconds + ",\"answers\":" + answers + "}";
    }

    private String id(MvcResult result) throws Exception {
        return (String) json.readValue(result.getResponse().getContentAsString(), Map.class).get("attemptId");
    }

    /** A valid access token, as Identity would issue it. */
    private String token(UUID userId, String supportLanguage) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(userId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("roles", List.of("LEARNER")).claim("lang", supportLanguage).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** Group 3 of Wolves: one "Choose TWO" question (A and D) with options A–D. */
    private void addChooseTwoQuestion() {
        long exercise = jdbc.queryForObject("select id from exercise where slug = 'yellowstone-wolves'", Long.class);
        long group = jdbc.queryForObject("""
                insert into question_group (exercise_id, position, type_code, instruction_html, choose_count)
                values (?, 3, 'MCMA', 'Choose <strong>TWO</strong> letters.', 2) returning id""", Long.class, exercise);
        long question = jdbc.queryForObject("""
                insert into question (exercise_id, group_id, position, stem_text)
                values (?, ?, 1, 'Which TWO changes followed the return of the wolves?') returning id""",
                Long.class, exercise, group);
        jdbc.update("""
                insert into question_option (question_id, option_key, label, position)
                values (?, 'A', 'Fewer elk', 1), (?, 'B', 'More tourists', 2), (?, 'C', 'Colder winters', 3),
                       (?, 'D', 'More beavers', 4)""", question, question, question, question);
        jdbc.update("insert into question_answer (question_id, value, position) values (?, 'A', 1), (?, 'D', 2)",
                question, question);
    }
}
