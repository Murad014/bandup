package com.bandup.api.vocabulary.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import com.bandup.api.vocabulary.domain.word.Cards;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.Lemma;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** The admin word card API (vocabulary requirement 8). No AI key in this context. Rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminVocabularyApiTest {

    private static final String API = "/api/v1/admin/vocabulary/words";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired WordCardRepository cards;

    private long predator;
    private long erosion;
    private String leyla;

    @BeforeEach
    void cards() throws Exception {
        predator = readyCard("predator");
        erosion = readyCard("erosion");
        leyla = UUID.randomUUID().toString();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                UUID.fromString(leyla), leyla + "@example.com");
        mvc.perform(as(leyla, "LEARNER", post("/api/v1/vocabulary/words")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"predator\"}")).andExpect(status().isCreated());
    }

    @Test
    void onlyAdminsAndAuthors() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(as(leyla, "LEARNER", get(API))).andExpect(status().isForbidden());
        mvc.perform(as(admin(), "AUTHOR", get(API))).andExpect(status().isOk());
        mvc.perform(as(admin(), "ADMIN", get(API))).andExpect(status().isOk());
    }

    @Test
    void theListFiltersAndCountsSaves() throws Exception {
        mvc.perform(as(admin(), "ADMIN", get(API).param("q", "pred"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[*].word").value(contains("predator")))
                .andExpect(jsonPath("$.cards[0].saves").value(1))
                .andExpect(jsonPath("$.cards[0].level").value("B2"))
                .andExpect(jsonPath("$.cards[0].status").value("READY"));
        mvc.perform(as(admin(), "ADMIN", get(API).param("status", "ready"))).andExpect(jsonPath("$.cards[*].word").value(hasItem("erosion")));
        mvc.perform(as(admin(), "ADMIN", get(API).param("status", "failed").param("q", "pred")))
                .andExpect(jsonPath("$.cards.length()").value(0));
        mvc.perform(as(admin(), "ADMIN", get(API).param("status", "lost"))).andExpect(status().isUnprocessableContent());
    }

    @Test
    void anEditIsCheckedAndEveryLearnerSeesIt() throws Exception {
        ObjectNode card = (ObjectNode) body(mvc.perform(as(admin(), "ADMIN", get(API + "/" + predator))).andExpect(status().isOk())
                .andExpect(jsonPath("$.forms").value(contains("predator", "predators")))
                .andExpect(jsonPath("$.saves").value(1)));
        ((ObjectNode) card.path("meaning")).put("en", "an animal that hunts and eats other animals").put("az", "yırtıcı");
        ((ArrayNode) card.path("forms")).add("Predatory");
        mvc.perform(as(admin(), "ADMIN", put(API + "/" + predator)).contentType(MediaType.APPLICATION_JSON).content(card.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("ADMIN"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.forms").value(contains("predator", "predators", "predatory")));
        mvc.perform(get("/api/v1/vocabulary/lookup").param("text", "predatory"))
                .andExpect(jsonPath("$.meaning.en").value("an animal that hunts and eats other animals"));
        mvc.perform(as(leyla, "LEARNER", get("/api/v1/vocabulary/words")))
                .andExpect(jsonPath("$.words[0].meaning.az").value("yırtıcı"));

        mvc.perform(as(admin(), "ADMIN", put(API + "/" + predator)).contentType(MediaType.APPLICATION_JSON).content(card.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void everyProblemIsReportedWithItsPath() throws Exception {
        ObjectNode card = (ObjectNode) body(mvc.perform(as(admin(), "ADMIN", get(API + "/" + predator))));
        card.put("level", "Z9");
        ((ObjectNode) card.path("pair")).put("answer", 9);
        ((ArrayNode) card.path("forms")).add("pred4tor");
        mvc.perform(as(admin(), "ADMIN", put(API + "/" + predator)).contentType(MediaType.APPLICATION_JSON).content(card.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(contains("forms[2]")));
        ((ArrayNode) card.path("forms")).remove(2);
        mvc.perform(as(admin(), "ADMIN", put(API + "/" + predator)).contentType(MediaType.APPLICATION_JSON).content(card.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field").value(contains("level", "pair.answer")));
        card.remove("version");
        mvc.perform(as(admin(), "ADMIN", put(API + "/" + predator)).contentType(MediaType.APPLICATION_JSON).content(card.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void aFormOfAnotherWordCantBeTaken() throws Exception {
        ObjectNode card = (ObjectNode) body(mvc.perform(as(admin(), "ADMIN", get(API + "/" + predator))));
        ((ArrayNode) card.path("forms")).add("erosion");
        mvc.perform(as(admin(), "ADMIN", put(API + "/" + predator)).contentType(MediaType.APPLICATION_JSON).content(card.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("“erosion” already belongs to another word."));
    }

    @Test
    void regenerateNeedsTheAiAndDeleteOnlyUnsavedWords() throws Exception {
        mvc.perform(as(admin(), "ADMIN", post(API + "/" + predator + "/regenerate"))).andExpect(status().isServiceUnavailable());
        mvc.perform(as(admin(), "ADMIN", delete(API + "/" + predator))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("1 learner has saved this word; it can't be deleted."));
        mvc.perform(as(admin(), "ADMIN", delete(API + "/" + erosion))).andExpect(status().isNoContent());
        mvc.perform(as(admin(), "ADMIN", get(API + "/" + erosion))).andExpect(status().isNotFound());
        mvc.perform(as(admin(), "ADMIN", get(API + "/0"))).andExpect(status().isNotFound());
    }

    private long readyCard(String lemma) {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm(lemma));
        cards.addIfFormFree(card);
        WordCard locked = cards.lock(card.id()).orElseThrow();
        locked.ready(new Lemma(lemma), Cards.formsOf(lemma), Cards.of(lemma), Cards.POLICY);
        cards.save(locked);
        return card.id().value();
    }

    private String admin() {
        return "00000000-0000-0000-0000-000000000001";
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder as(String subject, String role, MockHttpServletRequestBuilder request) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(subject).issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of(role)).claim("lang", "en").build();
        return request.header("Authorization", "Bearer " + jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue());
    }
}
