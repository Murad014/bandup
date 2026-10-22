package com.bandup.api.practice.domain.attempt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradingRule;
import com.bandup.api.practice.domain.attempt.valueobject.QuestionFamily;
import com.bandup.api.practice.domain.attempt.valueobject.Score;
import com.bandup.api.practice.domain.attempt.valueobject.WordLimit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs every case of the shared file {@code grading-cases.json} through the {@link Grader}. */
class GraderTest {

    private final Grader grader = new Grader();

    record Case(String name, GradingRule rule, GivenAnswer given, Score expected) {

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Case> sharedCases() throws Exception {
        try (InputStream in = GraderTest.class.getResourceAsStream("/grading-cases.json")) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            List<Case> cases = new ArrayList<>();
            for (JsonNode c : root.get("cases")) {
                cases.add(new Case(c.get("name").asString(), rule(c), given(c.get("given")),
                        new Score(c.get("points").asInt(), c.get("possible").asInt())));
            }
            return cases.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedCases")
    void scoresTheSharedCases(Case c) {
        assertThat(grader.score(c.rule(), c.given())).isEqualTo(c.expected());
    }

    @Test
    void theSharedFileCoversEveryFamily() throws Exception {
        assertThat(sharedCases().map(c -> c.rule().family()).distinct()).containsExactlyInAnyOrder(QuestionFamily.values());
    }

    private static GradingRule rule(JsonNode c) {
        List<String> accepted = new ArrayList<>();
        c.get("accepted").forEach(a -> accepted.add(a.asString()));
        return switch (QuestionFamily.ofCode(c.get("family").asString())) {
            case CHOICE_FIXED -> GradingRule.choiceFixed(accepted.getFirst());
            case MC_SINGLE -> GradingRule.mcSingle(accepted.getFirst());
            case MATCH -> GradingRule.match(accepted.getFirst());
            case MC_MULTI -> GradingRule.mcMulti(accepted, c.get("chooseCount").asInt());
            case GAP -> GradingRule.gap(accepted, c.has("wordLimit")
                    ? new WordLimit(c.get("wordLimit").get("maxWords").asInt(), c.get("wordLimit").get("numbersAllowed").asBoolean())
                    : null);
        };
    }

    private static GivenAnswer given(JsonNode given) {
        if (given.isArray()) {
            List<String> keys = new ArrayList<>();
            given.forEach(k -> keys.add(k.asString()));
            return new GivenAnswer.Keys(keys);
        }
        return new GivenAnswer.Text(given.asString());
    }
}
