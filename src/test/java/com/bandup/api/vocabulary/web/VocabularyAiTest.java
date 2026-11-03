package com.bandup.api.vocabulary.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.vocabulary.StubOpenRouter;
import com.bandup.api.vocabulary.application.CardRetryJob;
import com.bandup.api.vocabulary.domain.word.Cards;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saving words end to end with a fake OpenRouter (vocabulary requirement 3):
 * the database first, one AI call per new word, lemmas joining existing cards,
 * "not a word", retries, the daily limit and restart recovery. Not rolled
 * back (the card writer works after commit); every test cleans up.
 */
@SpringBootTest(properties = {"bandup.vocabulary.retry-every=1h", "bandup.vocabulary.ai.api-key=test-key-123",
        "bandup.vocabulary.ai.timeout=5s", "bandup.vocabulary.ai.model=test/model"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class VocabularyAiTest {

    static final StubOpenRouter AI = start();

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        registry.add("bandup.vocabulary.ai.base-url", AI::url);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired WordCardRepository cards;
    @Autowired CardRetryJob retryJob;

    private String leyla;
    private String kamal;

    @BeforeEach
    void learners() {
        AI.reset();
        leyla = newLearner();
        kamal = newLearner();
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        Thread.sleep(200); // let a late card writer finish before the rows go
        jdbc.update("delete from vocab_session");
        jdbc.update("delete from vocab_saved_word");
        jdbc.update("delete from vocab_word");
        jdbc.update("delete from vocab_ai_usage");
    }

    @AfterAll
    static void stop() {
        AI.stop();
    }

    @Test
    void aWordTheBankHasIsSavedWithoutAnyAiCall() throws Exception {
        readyCard("riverbank", "riverbanks");
        save(leyla, "Riverbanks,").andExpect(status().isCreated())
                .andExpect(jsonPath("$.word.word").value("riverbank"))
                .andExpect(jsonPath("$.word.cardStatus").value("READY"))
                .andExpect(jsonPath("$.word.state").value("NEW"))
                .andExpect(jsonPath("$.word.meaning.az").value("çay sahili"))
                .andExpect(jsonPath("$.alreadySaved").value(false));
        save(kamal, "riverbank").andExpect(status().isCreated());
        save(leyla, "riverbank").andExpect(status().isOk()).andExpect(jsonPath("$.alreadySaved").value(true));
        assertThat(AI.totalCalls()).as("no AI call for a known word").isZero();
        assertThat(usage(leyla)).isZero();
    }

    @Test
    void aNewWordCallsTheAiOnceThenEveryoneGetsItFromTheDatabase() throws Exception {
        AI.script((word, n) -> StubOpenRouter.card("thrive", List.of("thrives", "thrived", "thriving")).toString());
        JsonNode saved = body(save(leyla, "thriving").andExpect(status().isCreated())
                .andExpect(jsonPath("$.word.cardStatus").value("PREPARING"))
                .andExpect(jsonPath("$.word.word").value("thriving")));
        JsonNode ready = awaitReady(leyla, saved.path("word").path("id").asString());
        assertThat(ready.path("word").asString()).isEqualTo("thrive");
        assertThat(ready.path("meaning").path("en").asString()).isEqualTo("a thing used in the tests");
        assertThat(AI.calls("thriving")).isEqualTo(1);
        assertThat(usage(leyla)).isEqualTo(1);

        save(kamal, "Thrive").andExpect(status().isCreated()).andExpect(jsonPath("$.word.cardStatus").value("READY"));
        save(kamal, "thrived").andExpect(status().isOk()).andExpect(jsonPath("$.alreadySaved").value(true));
        assertThat(AI.totalCalls()).as("forms from the AI's card are known now").isEqualTo(1);
        assertThat(usage(kamal)).isZero();
    }

    @Test
    void twoLearnersSavingTheSameNewWordAtOnceCauseOneCall() throws Exception {
        AI.delay(800);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> a = () -> save(leyla, "abundant").andReturn().getResponse().getStatus();
        Callable<Integer> b = () -> save(kamal, "abundant").andReturn().getResponse().getStatus();
        List<Future<Integer>> results = pool.invokeAll(List.of(a, b));
        pool.shutdown();
        assertThat(results.get(0).get()).isEqualTo(201);
        assertThat(results.get(1).get()).isEqualTo(201);
        Thread.sleep(2500);
        assertThat(AI.calls("abundant")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(distinct word_id) from vocab_saved_word", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vocab_word", Integer.class)).isEqualTo(1);
    }

    @Test
    void aNewFormJoinsTheCardOfItsBaseForm() throws Exception {
        WordCard riverbank = readyCard("riverbank", "riverbanks");
        save(kamal, "riverbank").andExpect(status().isCreated());
        AI.script((word, n) -> StubOpenRouter.card("riverbank", List.of("river banks")).toString());
        JsonNode saved = body(save(leyla, "river banks").andExpect(status().isCreated()));
        JsonNode ready = awaitReady(leyla, saved.path("word").path("id").asString());
        assertThat(ready.path("word").asString()).isEqualTo("riverbank");
        assertThat(jdbc.queryForObject("select count(*) from vocab_word", Integer.class)).as("no second card").isEqualTo(1);
        assertThat(cards.findById(riverbank.id()).orElseThrow().forms()).contains(new WordForm("river banks"));
        assertThat(cards.findByForm(new WordForm("river banks")).orElseThrow().id()).isEqualTo(riverbank.id());
    }

    @Test
    void notAWordIsRememberedAndNeverAskedAgain() throws Exception {
        AI.script((word, n) -> "{\"status\": \"NOT_A_WORD\", \"reason\": \"a made-up word\"}");
        JsonNode saved = body(save(leyla, "zorbify").andExpect(status().isCreated()));
        await(() -> jdbc.queryForObject("select count(*) from vocab_saved_word", Integer.class) == 0);
        mvc.perform(as(leyla, get("/api/v1/vocabulary/words").param("ids", saved.path("word").path("id").asString())))
                .andExpect(jsonPath("$.length()").value(0));
        save(kamal, "Zorbify").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("text"))
                .andExpect(jsonPath("$.errors[0].message").value("“zorbify” isn't an English word we can teach."));
        assertThat(AI.calls("zorbify")).isEqualTo(1);
    }

    @Test
    void aBadAnswerIsRetriedOnceWithItsProblemsThenTheWordFails() throws Exception {
        AI.script((word, n) -> "this is not json");
        JsonNode saved = body(save(leyla, "erosion").andExpect(status().isCreated()));
        String id = saved.path("word").path("id").asString();
        await(() -> "FAILED".equals(word(leyla, id).path("cardStatus").asString()));
        assertThat(AI.calls("erosion")).isEqualTo(2);
        assertThat(AI.requests().get(1).user()).contains("previous answer had these problems", "Return only one JSON object");

        AI.script((word, n) -> StubOpenRouter.card("erosion", List.of()).toString());
        mvc.perform(as(leyla, post("/api/v1/vocabulary/words/" + id + "/retry"))).andExpect(status().isAccepted());
        awaitReady(leyla, id);
        assertThat(AI.calls("erosion")).isEqualTo(3);
        mvc.perform(as(leyla, post("/api/v1/vocabulary/words/" + id + "/retry"))).andExpect(status().isUnprocessableContent());
    }

    @Test
    void anInvalidCardIsSentBackWithTheRulesItBroke() throws Exception {
        AI.script((word, n) -> n == 1
                ? StubOpenRouter.card("decline", List.of()).put("level", "Z9").put("ipa", "decline").toString()
                : StubOpenRouter.card("decline", List.of()).toString());
        JsonNode saved = body(save(leyla, "decline").andExpect(status().isCreated()));
        awaitReady(leyla, saved.path("word").path("id").asString());
        assertThat(AI.calls("decline")).isEqualTo(2);
        assertThat(AI.requests().get(1).user()).contains("level: Use A1", "ipa: Write the IPA");
    }

    @Test
    void theRequestCarriesTheKeyModelAndThePassageContext(CapturedOutput output) throws Exception {
        JsonNode saved = body(mvc.perform(as(leyla, post("/api/v1/vocabulary/words")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"predator\", \"source\": {\"exercise\": \"yellowstone-wolves\","
                        + " \"sentence\": \"Without their main predator, elk populations expanded.\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.word.source.title").isNotEmpty()));
        awaitReady(leyla, saved.path("word").path("id").asString());
        StubOpenRouter.Call call = AI.requests().get(0);
        assertThat(call.authorization()).isEqualTo("Bearer test-key-123");
        assertThat(call.body().path("model").asString()).isEqualTo("test/model");
        assertThat(call.system()).contains("NOT_A_WORD", "wrongMeanings");
        assertThat(call.user()).contains("WORD: predator", "CONTEXT: \"Without their main predator", "PASSAGE MEANING:");
        assertThat(output.getAll()).contains("Word card AI call for \"predator\"").doesNotContain("test-key-123");
    }

    @Test
    void theDailyLimitCountsOnlyNewWords() throws Exception {
        readyCard("riverbank", "riverbanks");
        jdbc.update("insert into vocab_ai_usage (user_id, day, count) select id, ?, 30 from app_user where public_id = ?",
                LocalDate.now(ZoneId.of("Asia/Baku")), UUID.fromString(leyla));
        save(leyla, "habitat").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("You can add more new words tomorrow."));
        save(leyla, "riverbanks").andExpect(status().isCreated());
        assertThat(AI.totalCalls()).isZero();
    }

    @Test
    void junkIsRefusedAndGuestsMustSignIn() throws Exception {
        save(leyla, "hello123").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("text"));
        save(leyla, "one two three four").andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/vocabulary/words").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"word\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(AI.totalCalls()).isZero();
    }

    @Test
    void anAiErrorFailsTheWordAndTheLearnerCanTryAgain() throws Exception {
        AI.status(500);
        JsonNode saved = body(save(leyla, "habitat").andExpect(status().isCreated()));
        String id = saved.path("word").path("id").asString();
        await(() -> "FAILED".equals(word(leyla, id).path("cardStatus").asString()));
        assertThat(AI.calls("habitat")).isEqualTo(1);
    }

    @Test
    void anAdminRegeneratesACardAndLearnersKeepTheirWord() throws Exception {
        WordCard card = readyCard("riverbank", "riverbanks");
        JsonNode saved = body(save(leyla, "riverbank").andExpect(status().isCreated()));
        AI.script((word, n) -> StubOpenRouter.card("riverbank", List.of("riverbanks")).toString());
        mvc.perform(asAdmin(post("/api/v1/admin/vocabulary/words/" + card.id().value() + "/regenerate")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PREPARING"));
        JsonNode ready = awaitReady(leyla, saved.path("word").path("id").asString());
        assertThat(ready.path("meaning").path("en").asString()).isEqualTo("a thing used in the tests");
        assertThat(AI.calls("riverbank")).isEqualTo(1);
        assertThat(usage(leyla)).as("an admin's regenerate isn't the learner's").isZero();
    }

    @Test
    void cardsLeftPreparingAreWrittenAgain() throws Exception {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm("vegetation"));
        cards.addIfFormFree(card);
        jdbc.update("update vocab_word set updated_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(600)), card.id().value());
        assertThat(retryJob.retryStale()).isEqualTo(1);
        assertThat(cards.findById(card.id()).orElseThrow().isReady()).isTrue();
        assertThat(retryJob.retryStale()).isZero();
    }

    @Test
    void theLookupIsPublicAndNeverCallsTheAi() throws Exception {
        readyCard("riverbank", "riverbanks");
        mvc.perform(get("/api/v1/vocabulary/lookup").param("text", "Riverbanks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.word").value("riverbank")).andExpect(jsonPath("$.meaning.en").isNotEmpty())
                .andExpect(jsonPath("$.gap").doesNotExist()).andExpect(jsonPath("$.wrongMeanings").doesNotExist());
        mvc.perform(get("/api/v1/vocabulary/lookup").param("text", "unknownword")).andExpect(status().isNotFound());
        assertThat(AI.totalCalls()).isZero();
    }

    // ------------------------------------------------------------- helpers

    private static StubOpenRouter start() {
        try {
            return new StubOpenRouter();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private WordCard readyCard(String lemma, String form) {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm(lemma));
        cards.addIfFormFree(card);
        WordCard locked = cards.findById(card.id()).orElseThrow();
        locked.ready(Cards.LEMMA, List.of(new WordForm(lemma), new WordForm(form)), Cards.riverbank(), Cards.POLICY);
        cards.save(locked);
        return cards.findById(card.id()).orElseThrow();
    }

    private ResultActions save(String learner, String text) throws Exception {
        return mvc.perform(as(learner, post("/api/v1/vocabulary/words")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("text", text))));
    }

    private JsonNode word(String learner, String id) throws Exception {
        return body(mvc.perform(as(learner, get("/api/v1/vocabulary/words").param("ids", id)))).path(0);
    }

    private JsonNode awaitReady(String learner, String id) throws Exception {
        await(() -> "READY".equals(word(learner, id).path("cardStatus").asString()));
        return word(learner, id);
    }

    private interface Check {
        boolean ok() throws Exception;
    }

    private static void await(Check check) throws Exception {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out");
    }

    private int usage(String learner) {
        return jdbc.queryForObject("""
                select coalesce(sum(u.count), 0) from vocab_ai_usage u join app_user a on a.id = u.user_id
                where a.public_id = ?""", Integer.class, UUID.fromString(learner));
    }

    private String newLearner() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                id, id + "@example.com");
        return id.toString();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(UUID.randomUUID().toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of("ADMIN")).claim("lang", "en").build();
        return request.header("Authorization", "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(String learner,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(learner).issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of("LEARNER")).claim("lang", "en").build();
        return request.header("Authorization", "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue());
    }
}
