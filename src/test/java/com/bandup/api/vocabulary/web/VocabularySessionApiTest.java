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
import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.Meaning;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * My words, sets and sessions end to end (vocabulary requirements 4–7): the
 * page, learn sessions graded on the server, rounds of the sets, ownership and
 * errors.
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
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.counts.fresh").value(3))
                .andExpect(jsonPath("$.newReady").value(3))
                .andExpect(jsonPath("$.sets.current").value(0))
                .andExpect(jsonPath("$.sets.sets.length()").value(0))
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
        assertThat(jdbc.queryForObject("select count(*) from vocab_saved_word where state = 'LEARNING' and streak = 0"
                + " and learned_at is not null and box is null", Integer.class)).isEqualTo(5);
        mvc.perform(as(leyla, get(API + "/words").param("state", "learning")))
                .andExpect(jsonPath("$.sets.current").value(1))
                .andExpect(jsonPath("$.sets.sets[0].size").value(5))
                .andExpect(jsonPath("$.sets.sets[0].streaks").value(contains(0, 0, 0, 0, 0)))
                .andExpect(jsonPath("$.words[0].set").value(1))
                .andExpect(jsonPath("$.words[0].streak").value(0));
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
    void nothingToLearnAndOldKindsAreGone() throws Exception {
        start(leyla, "learn", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("There are no new words ready to learn."));
        start(leyla, "review", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Use learn or round."));
        start(leyla, "recall", null).andExpect(status().isUnprocessableContent());
        start(leyla, "later", null).andExpect(status().isUnprocessableContent());
        mvc.perform(post(API + "/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"learn\"}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------- sets

    /** 27 learned words after 2 rounds: Set 1 has 25 (23 of them mastered, one right once), Set 2 has 2. */
    @Test
    void aRoundAsksTheOpenSetWithNoHintAndOnlyFirstTriesMoveWords() throws Exception {
        List<String> words = lemmas(27);
        words.forEach(this::readyCard);
        for (String w : words) {
            save(leyla, w);
        }
        learn(words);
        for (String w : words.subList(0, 23)) {
            progress(w, "MASTERED", 2, 2);
        }
        progress(words.get(23), "LEARNING", 1, 2);
        progress(words.get(24), "LEARNING", 0, 2);
        String once = words.get(23);
        String zero = words.get(24);

        mvc.perform(as(leyla, get(API + "/sets"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(1))
                .andExpect(jsonPath("$.sets[0].size").value(25))
                .andExpect(jsonPath("$.sets[0].mastered").value(23))
                .andExpect(jsonPath("$.sets[0].rounds").value(2))
                .andExpect(jsonPath("$.sets[0].status").value("OPEN"))
                .andExpect(jsonPath("$.sets[0].streaks[23]").value(1))
                .andExpect(jsonPath("$.sets[0].streaks[24]").value(0))
                .andExpect(jsonPath("$.sets[1].size").value(2))
                .andExpect(jsonPath("$.sets[1].status").value("LOCKED"));

        // round 3 of Set 1: only its 2 words not mastered, no hint, no answer in the payload
        JsonNode round = body(start(leyla, "round", null).andExpect(status().isCreated()));
        String text = round.toString();
        KEYS.forEach(key -> assertThat(text).as(key).doesNotContain(key));
        assertThat(round.path("kind").asString()).isEqualTo("ROUND");
        assertThat(round.path("set").path("number").asInt()).isEqualTo(1);
        assertThat(round.path("set").path("count").asInt()).isEqualTo(2);
        assertThat(round.path("set").path("round").asInt()).isEqualTo(3);
        assertThat(round.path("words")).isEmpty();
        assertThat(types(round)).containsOnly("translate").hasSize(2);
        round.path("tasks").forEach(t -> assertThat(t.path("hint").isNull()).as("no hint").isTrue());
        assertThat(azs(round)).containsExactlyInAnyOrder("az " + once, "az " + zero);
        String id = round.path("sessionId").asString();
        answer(leyla, id, taskFor(round, once), once.toUpperCase() + "s")
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.step.streak").value(2))
                .andExpect(jsonPath("$.step.mastered").value(true));
        dontKnow(leyla, id, taskFor(round, zero))
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.expected").value(zero))
                .andExpect(jsonPath("$.step.streak").value(0))
                .andExpect(jsonPath("$.step.changed").value(true));
        answer(leyla, id, taskFor(round, zero), zero)
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.step").doesNotExist());
        assertThat(streak(zero)).as("a retry in the round doesn't count").isZero();

        // rounds 4 and 5: the last word, right twice in a row → Set 1 mastered, Set 2 opens
        for (int r = 4; r <= 5; r++) {
            JsonNode next = body(start(leyla, "round", null));
            assertThat(next.path("set").path("round").asInt()).isEqualTo(r);
            assertThat(azs(next)).containsExactly("az " + zero);
            answer(leyla, next.path("sessionId").asString(), taskFor(next, zero), zero)
                    .andExpect(jsonPath("$.step.streak").value(r - 3))
                    .andExpect(jsonPath("$.step.mastered").value(r == 5));
        }
        mvc.perform(as(leyla, get(API + "/sets")))
                .andExpect(jsonPath("$.current").value(2))
                .andExpect(jsonPath("$.sets[0].status").value("MASTERED"))
                .andExpect(jsonPath("$.sets[0].rounds").value(5))
                .andExpect(jsonPath("$.sets[1].status").value("OPEN"));
        JsonNode set2 = body(start(leyla, "round", null));
        assertThat(set2.path("set").path("number").asInt()).isEqualTo(2);
        assertThat(azs(set2)).containsExactlyInAnyOrder("az " + words.get(25), "az " + words.get(26));
        mvc.perform(as(leyla, get(API + "/words").param("q", words.get(26))))
                .andExpect(jsonPath("$.words[0].set").value(2));
        assertThat(jdbc.queryForObject("select count(*) from vocab_saved_word where box is not null or due_on is not null",
                Integer.class)).as("the old review columns aren't used").isZero();
    }

    @Test
    void aRoundAcceptsTheLearnersOtherWordWithTheSameAzerbaijani() throws Exception {
        List<String> words = List.of("abundant", "thrive", "predator");
        for (String w : words) {
            save(leyla, w);
        }
        learn(words);
        azOf("abundant", " Bol ");
        azOf("thrive", "bol");
        JsonNode round = body(start(leyla, "round", null).andExpect(status().isCreated()));
        assertThat(types(round)).hasSize(3);
        String id = round.path("sessionId").asString();
        int bol = 0;
        for (JsonNode task : round.path("tasks")) {
            if (task.path("az").asString().strip().equalsIgnoreCase("bol")) {
                answer(leyla, id, task.path("id").asString(), "thrive").andExpect(jsonPath("$.correct").value(true));
                bol++;
            }
        }
        assertThat(bol).isEqualTo(2);
    }

    @Test
    void aRoundNeedsLearnedWordsAndEndsWhenEverySetIsMastered() throws Exception {
        save(leyla, "predator");
        start(leyla, "round", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Learn some words first — they join Set 1."));
        learn(List.of("predator"));
        progress("predator", "MASTERED", 2, 2);
        start(leyla, "round", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("Every set is mastered. Learn new words to start the next set."));
        mvc.perform(as(leyla, get(API + "/sets"))).andExpect(jsonPath("$.current").value(0))
                .andExpect(jsonPath("$.sets[0].status").value("MASTERED"));
        mvc.perform(get(API + "/sets")).andExpect(status().isUnauthorized());
    }

    /** Test words: "lexaa", "lexab", … (letters only, so they pass the word rules). */
    private static List<String> lemmas(int n) {
        List<String> words = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            words.add("lex" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
        }
        return words;
    }

    /** Marks Leyla's words learned, one second apart, in this order. */
    private void learn(List<String> words) {
        for (int i = 0; i < words.size(); i++) {
            jdbc.update("update vocab_saved_word set state = 'LEARNING', learned_at = ?"
                    + " where word_id = (select word_id from vocab_word_form where form = ?)",
                    java.sql.Timestamp.from(Instant.parse("2026-11-01T08:00:00Z").plusSeconds(i)), words.get(i));
        }
    }

    private void progress(String word, String state, int streak, int rounds) {
        jdbc.update("update vocab_saved_word set state = ?, streak = ?, rounds = ?"
                + " where word_id = (select word_id from vocab_word_form where form = ?)", state, streak, rounds, word);
    }

    private int streak(String word) {
        return jdbc.queryForObject("select streak from vocab_saved_word"
                + " where word_id = (select word_id from vocab_word_form where form = ?)", Integer.class, word);
    }

    private static List<String> azs(JsonNode session) {
        List<String> azs = new ArrayList<>();
        session.path("tasks").forEach(t -> azs.add(t.path("az").asString()));
        return azs;
    }

    private static String taskFor(JsonNode session, String word) {
        for (JsonNode t : session.path("tasks")) {
            if (t.path("az").asString().equals("az " + word)) {
                return t.path("id").asString();
            }
        }
        throw new AssertionError(word + " not asked");
    }

    /** An admin gives the card another Azerbaijani meaning. */
    private void azOf(String lemma, String az) {
        WordCard card = cards.lock(cards.findByForm(new WordForm(lemma)).orElseThrow().id()).orElseThrow();
        CardContent c = card.content().orElseThrow();
        card.edit(card.lemma().orElseThrow(), card.forms(), new CardContent(c.pos(), c.level(), c.ipa(),
                new Meaning(c.meaning().en(), az), c.examples(), c.collocations(), c.family(), c.gap(), c.pair(),
                c.wrongMeanings()), Cards.POLICY);
        cards.save(card);
    }

    private ResultActions dontKnow(String learner, String session, String task) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("taskId", task);
        body.put("answer", null);
        return mvc.perform(as(learner, post(API + "/sessions/" + session + "/answers")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
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
