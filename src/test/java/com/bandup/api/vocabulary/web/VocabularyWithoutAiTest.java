package com.bandup.api.vocabulary.web;

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
import org.springframework.transaction.annotation.Transactional;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.vocabulary.domain.word.Cards;
import com.bandup.api.vocabulary.domain.word.aggregate.WordCard;
import com.bandup.api.vocabulary.domain.word.repository.WordCardRepository;
import com.bandup.api.vocabulary.domain.word.valueobject.WordForm;

/** Without an AI key (vocabulary requirement 3.9): known words still work, new words get 503. Rolled back. */
@SpringBootTest(properties = "bandup.vocabulary.ai.api-key=")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class VocabularyWithoutAiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired WordCardRepository cards;

    @Test
    void knownWordsWorkAndNewWordsWait() throws Exception {
        WordCard card = WordCard.preparing(cards.nextId(), new WordForm("riverbank"));
        cards.addIfFormFree(card);
        WordCard locked = cards.lock(card.id()).orElseThrow();
        locked.ready(Cards.LEMMA, Cards.FORMS, Cards.riverbank(), Cards.POLICY);
        cards.save(locked);
        String token = token();
        mvc.perform(post("/api/v1/vocabulary/words").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"riverbanks\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/vocabulary/words").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"habitat\"}")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("New words can't be prepared right now. Please try again later."));
    }

    private String token() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (public_id, email, ui_language, support_language) values (?, ?, 'en', 'en')",
                id, id + "@example.com");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("bandup").subject(id.toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(900)).claim("roles", List.of("LEARNER")).claim("lang", "en").build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
