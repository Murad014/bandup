package com.bandup.api.vocabulary.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.vocabulary.domain.word.Cards;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * My words and sessions end to end (vocabulary requirements 4–7): the page,
 * learn sessions graded on the server, review boxes, ownership and errors.
 * The words are in the bank already (no AI). Rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class VocabularySessionApiTest {

    private static final String API = "/api/v1/vocabulary";
    private static final List<String> WORDS = List.of("predator", "erosion", "abundant", "decline", "thrive", "habitat");
    /** Fields that would give an answer away; never in a learner response before answering. */
    static final List<String> KEYS = List.of("\"answer\"", "\"accept\"", "\"gapAnswer\"", "\"pairAnswer\"",
            "\"wrongMeanings\"", "\"expected\"");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired WordCardRepository cards;

    private String leyla;
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Baku"));

    @BeforeEach
    void cardsInTheBank() {
        WORDS.forEach(this::readyCard);
        leyla = newLearner();
    }

    // ---------------------------------------------------------------- page

    @Test
    void myWordsShowTotalsTabsAndSearch() throws Exception {
        for (String w : List.of("predator", "erosion", "abundant")) {
            save(leyla, w).andExpect(status().isCreated());
        }
        mvc.perform(as(leyla, get(API + "/words"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.all").value(3))
                .andExpect(jsonPath("$.counts.fresh").value(3))
                .andExpect(jsonPath("$.newReady").value(3))
                .andExpect(jsonPath("$.due").value(0))
                .andExpect(jsonPath("$.words[*].word").value(contains("abundant", "erosion", "predator")))
                .andExpect(jsonPath("$.words[0].meaning.az").value("az abundant"))
                .andExpect(jsonPath("$.words[0].state").value("NEW"));
        mvc.perform(as(leyla, get(API + "/words").param("q", "ERO"))).andExpect(jsonPath("$.words[*].word").value(contains("erosion")));
        mvc.perform(as(leyla, get(API + "/words").param("state", "learning"))).andExpect(jsonPath("$.words.length()").value(0));
        mvc.perform(as(leyla, get(API + "/words").param("state", "old"))).andExpect(status().isUnprocessableContent());
        mvc.perform(get(API + "/words")).andExpect(status().isUnauthorized());
    }

    @Test
    void removingAWordKeepsTheCard() throws Exception {
        String id = body(save(leyla, "predator")).path("word").path("id").asString();
        String kamal = newLearner();
        mvc.perform(as(kamal, delete(API + "/words/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(leyla, delete(API + "/words/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(leyla, get(API + "/words"))).andExpect(jsonPath("$.counts.all").value(0));
        assertThat(cards.findByForm(new WordForm("predator"))).isPresent();
    }

    // --------------------------------------------------------------- learn

    @Test
    void aLearnSessionTeachesFiveWordsAndAWrongTaskComesBack() throws Exception {
        WORDS.forEach(w -> {
            try {
                save(leyla, w);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        JsonNode session = body(start(leyla, "learn", null).andExpect(status().isCreated()));
        String text = session.toString();
        KEYS.forEach(key -> assertThat(text).as(key).doesNotContain(key));
        assertThat(session.path("words")).hasSize(5);
        assertThat(session.path("words").path(0).path("word").asString()).isEqualTo("predator");
        assertThat(session.path("words").path(0).path("meaningAz").asString()).isEqualTo("az predator");
        assertThat(types(session)).containsExactly("meaning", "match", "gap", "type", "listen", "pair", "translate");
        assertThat(session.path("total").asInt()).isEqualTo(5);
        String id = session.path("sessionId").asString();

        // practise: right meaning, then the match
        JsonNode meaning = session.path("tasks").path(0);
        int right = optionsIndex(meaning.path("options"), Cards.meaningOf("predator"));
        answer(leyla, id, "t1", right).andExpect(jsonPath("$.correct").value(true));
        JsonNode match = session.path("tasks").path(1);
        List<Integer> picks = new ArrayList<>();
        for (JsonNode term : match.path("terms")) {
            picks.add(optionsIndex(match.path("meanings"), Cards.meaningOf(term.asString())));
        }
        answer(leyla, id, "t2", picks).andExpect(jsonPath("$.correct").value(true));

        // master: the gap wrong first, so it comes back
        answer(leyla, id, "t3", "nonsense").andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.expected").value("predator"))
                .andExpect(jsonPath("$.explanation").value("predator — " + Cards.meaningOf("predator")))
                .andExpect(jsonPath("$.mastered").value(0));
        answer(leyla, id, "t4", "Erosion.").andExpect(jsonPath("$.correct").value(true));
        answer(leyla, id, "t5", "abundants").andExpect(jsonPath("$.correct").value(true));
        JsonNode pair = session.path("tasks").path(5);
        answer(leyla, id, "t6", optionsIndex(pair.path("options"), "decline")).andExpect(jsonPath("$.correct").value(true));
        answer(leyla, id, "t7", "thrive").andExpect(jsonPath("$.finished").value(false));
        mvc.perform(as(leyla, get(API + "/sessions/" + id))).andExpect(jsonPath("$.queue").value(contains("t3")));
        answer(leyla, id, "t3", "predator").andExpect(jsonPath("$.finished").value(true)).andExpect(jsonPath("$.mastered").value(5));

        mvc.perform(as(leyla, get(API + "/words"))).andExpect(jsonPath("$.counts.learning").value(5))
                .andExpect(jsonPath("$.counts.fresh").value(1));
        assertThat(jdbc.queryForObject("select count(*) from vocab_saved_word where state = 'LEARNING' and box = 1 and due_on = ?",
                Integer.class, today.plusDays(1))).isEqualTo(5);
        answer(leyla, id, "t3", "predator").andExpect(status().isConflict());
    }

    @Test
    void aLearnSessionCanTakeOnePassagesWords() throws Exception {
        mvc.perform(as(leyla, post(API + "/words")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"habitat\", \"source\": {\"exercise\": \"yellowstone-wolves\", \"sentence\": \"…habitat for beavers.\"}}"))
                .andExpect(status().isCreated());
        save(leyla, "predator");
        JsonNode session = body(start(leyla, "learn", "yellowstone-wolves"));
        assertThat(session.path("words")).hasSize(1);
        assertThat(session.path("words").path(0).path("word").asString()).isEqualTo("habitat");
        assertThat(session.path("words").path(0).path("sentence").asString()).isEqualTo("…habitat for beavers.");
        assertThat(session.path("words").path(0).path("from").asString()).isNotBlank();
        assertThat(types(session)).containsExactly("meaning", "gap");
        mvc.perform(as(leyla, get(API + "/words").param("exercise", "yellowstone-wolves")))
                .andExpect(jsonPath("$.words[*].word").value(contains("habitat")))
                .andExpect(jsonPath("$.words[0].forms").value(contains("habitat", "habitats")))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void nothingToLearnOrReview() throws Exception {
        start(leyla, "learn", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("There are no new words ready to learn."));
        start(leyla, "review", null).andExpect(status().isUnprocessableContent());
        start(leyla, "later", null).andExpect(status().isUnprocessableContent());
        mvc.perform(post(API + "/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"learn\"}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------- review

    @Test
    void aReviewMovesEachWordOnceADayAndNeverNamesIt() throws Exception {
        for (String w : List.of("predator", "erosion")) {
            save(leyla, w);
        }
        jdbc.update("update vocab_saved_word set state = 'LEARNING', box = 2, due_on = ?, last_answered_on = ?",
                today, today.minusDays(3));
        JsonNode session = body(start(leyla, "review", null).andExpect(status().isCreated()));
        String text = session.toString();
        KEYS.forEach(key -> assertThat(text).as(key).doesNotContain(key));
        assertThat(session.path("words")).isEmpty();
        assertThat(types(session)).doesNotContain("listen", "meaning", "match").hasSize(2);
        for (JsonNode task : session.path("tasks")) {
            assertThat(task.path("term").isNull()).isTrue();
            assertThat(task.path("speak").isNull()).isTrue();
            assertThat(task.path("from").asString()).isEqualTo("your words");
            assertThat(task.path("box").asInt()).isEqualTo(2);
        }
        String id = session.path("sessionId").asString();
        JsonNode first = session.path("tasks").path(0);
        answer(leyla, id, first.path("id").asString(), wrong(first))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.schedule.from").value(2))
                .andExpect(jsonPath("$.schedule.box").value(1))
                .andExpect(jsonPath("$.schedule.nextDueOn").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.schedule.changed").value(true));
        JsonNode second = session.path("tasks").path(1);
        answer(leyla, id, second.path("id").asString(), right(second))
                .andExpect(jsonPath("$.schedule.box").value(3))
                .andExpect(jsonPath("$.schedule.nextDueOn").value(today.plusDays(7).toString()));
        answer(leyla, id, first.path("id").asString(), right(first))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.schedule.changed").value(false))
                .andExpect(jsonPath("$.schedule.box").value(1));
        mvc.perform(as(leyla, get(API + "/words"))).andExpect(jsonPath("$.due").value(0));
    }

    @Test
    void sessionsBelongToTheirLearnerAndCheckAnswers() throws Exception {
        save(leyla, "predator");
        save(leyla, "erosion");
        String id = body(start(leyla, "learn", null)).path("sessionId").asString();
        String kamal = newLearner();
        mvc.perform(as(kamal, get(API + "/sessions/" + id))).andExpect(status().isNotFound());
        answer(kamal, id, "t1", 0).andExpect(status().isNotFound());
        answer(leyla, id, "t9", 0).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("taskId"));
        answer(leyla, id, "x", 0).andExpect(status().isUnprocessableContent());
        answer(leyla, id, "t1", "text").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("answer"));
        answer(leyla, id, "t1", Map.of("a", 1)).andExpect(status().isUnprocessableContent());
        mvc.perform(as(leyla, get(API + "/sessions/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- helpers

    private static List<String> types(JsonNode session) {
        List<String> types = new ArrayList<>();
        session.path("tasks").forEach(t -> types.add(t.path("type").asString()));
        return types;
    }

    private static int optionsIndex(JsonNode options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.path(i).asString().equals(value)) {
                return i;
            }
        }
        throw new AssertionError(value + " not in " + options);
    }

    /** The right answer of a review task, worked out from the test cards (their forms and fixed sentences). */
    private Object right(JsonNode task) {
        return switch (task.path("type").asString()) {
            case "pair" -> {
                for (int i = 0; i < 4; i++) {
                    if (WORDS.contains(task.path("options").path(i).asString())) {
                        yield i;
                    }
                }
                throw new AssertionError(task.toString());
            }
            case "translate" -> task.path("az").asString().substring(3);
            case "gap", "type" -> WORDS.stream().filter(w -> task.path("hint").asString()
                    .equals(com.bandup.api.vocabulary.domain.session.service.TaskBuilder.hint(w))).findFirst().orElseThrow();
            default -> throw new AssertionError(task.toString());
        };
    }

    private static Object wrong(JsonNode task) {
        return task.path("type").asString().equals("pair") ? (task.path("options").path(0).asString().equals("table") ? 1 : 0)
                : "nonsense";
    }

    private ResultActions save(String learner, String word) throws Exception {
        return mvc.perform(as(learner, post(API + "/words")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("text", word))));
    }

    private ResultActions start(String learner, String kind, String exercise) throws Exception {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("kind", kind);
        body.put("exercise", exercise);
        return mvc.perform(as(learner, post(API + "/sessions")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions answer(String learner, String session, String task, Object answer) throws Exception {
        return mvc.perform(as(learner, post(API + "/sessions/" + session + "/answers")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("taskId", task, "answer", answer))));
    }

    private void readyCard(String lemma) {
        if (cards.findByForm(new WordForm(lemma)).isPresent()) {
            return;
        }
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm(lemma));
        cards.addIfFormFree(card);
        WordCard locked = cards.lock(card.id()).orElseThrow();
        locked.ready(new Lemma(lemma), Cards.formsOf(lemma), Cards.of(lemma), Cards.POLICY);
        cards.save(locked);
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

    private MockHttpServletRequestBuilder as(String learner, MockHttpServletRequestBuilder request) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(learner).issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of("LEARNER")).claim("lang", "en").build();
        return request.header("Authorization", "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue());
    }
}
