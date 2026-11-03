package com.bandup.api.vocabulary.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.vocabulary.StubOpenRouter;
import com.bandup.api.vocabulary.application.port.WordCardAi.CardAnswer;
import com.bandup.api.vocabulary.application.port.WordCardAi.CardRequest;

import tools.jackson.databind.json.JsonMapper;

/** Reading the model's answer and building the prompt. */
class OpenRouterParseTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void aCardWithOrWithoutFences() {
        String card = StubOpenRouter.card("thrive", List.of("thrives")).toString();
        for (String text : List.of(card, "```json\n" + card + "\n```")) {
            CardAnswer answer = OpenRouterWordCards.parse(completion(text));
            assertThat(answer).isInstanceOfSatisfying(CardAnswer.Card.class, c -> {
                assertThat(c.lemma()).isEqualTo("thrive");
                assertThat(c.forms()).containsExactly("thrive", "thrives");
                assertThat(c.content().pair().options()).hasSize(4);
            });
        }
    }

    @Test
    void notAWordAndUnreadableAnswers() {
        assertThat(OpenRouterWordCards.parse(completion("{\"status\": \"NOT_A_WORD\", \"reason\": \"a name\"}")))
                .isEqualTo(new CardAnswer.NotAWord("a name"));
        assertThat(OpenRouterWordCards.parse(completion("Sure! Here it is"))).isInstanceOf(CardAnswer.Unreadable.class);
        assertThat(OpenRouterWordCards.parse(completion("[1, 2]"))).isInstanceOf(CardAnswer.Unreadable.class);
        assertThat(OpenRouterWordCards.parse(completion("{\"status\": \"MAYBE\"}"))).isInstanceOf(CardAnswer.Unreadable.class);
        assertThat(OpenRouterWordCards.parse("not even json")).isInstanceOf(CardAnswer.Unreadable.class);
    }

    @Test
    void thePromptCarriesTheContextAndTheProblems() {
        CardRequest request = new CardRequest("predator", "Without their main \"predator\", elk grew.", "a hunter",
                "yırtıcı", List.of());
        assertThat(CardPrompt.user(request)).isEqualTo("WORD: predator\nCONTEXT: \"Without their main 'predator', elk grew.\""
                + "\nPASSAGE MEANING: \"a hunter\" / az: \"yırtıcı\"");
        assertThat(CardPrompt.user(request.retry(List.of("level: Use A1…")))).endsWith("fix all of them:\n- level: Use A1…");
        assertThat(CardPrompt.user(new CardRequest("thrive", null, null, null, List.of()))).isEqualTo("WORD: thrive");
    }

    @Test
    void theKeyIsNeverPrinted() {
        assertThat(new AiProperties("secret-key", null, null, null, 0).toString()).doesNotContain("secret-key").contains("(set)");
        AiProperties defaults = new AiProperties(null, null, null, null, 0);
        assertThat(defaults.dailyLimit()).isEqualTo(30);
        assertThat(defaults.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
    }

    private static String completion(String content) {
        var root = JSON.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return root.toString();
    }
}
