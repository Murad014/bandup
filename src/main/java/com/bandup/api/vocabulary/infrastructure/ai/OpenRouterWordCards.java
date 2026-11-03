package com.bandup.api.vocabulary.infrastructure.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.bandup.api.vocabulary.application.port.WordCardAi;
import com.bandup.api.vocabulary.infrastructure.persistence.CardJson;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link WordCardAi} on OpenRouter's chat completions API, with the JDK's
 * {@link HttpClient} (no extra library). The key goes only into the
 * Authorization header and is never logged. Each call is logged with the
 * word, model, time and result.
 */
@Component
@Slf4j
class OpenRouterWordCards implements WordCardAi {

    private final AiProperties properties;
    private final HttpClient http;

    OpenRouterWordCards(AiProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    }

    @Override
    public boolean available() {
        return !properties.apiKey().isEmpty();
    }

    @Override
    public CardAnswer write(CardRequest request) {
        if (!available()) {
            throw new AiUnavailableException("no API key is configured", null);
        }
        long started = System.nanoTime();
        HttpResponse<String> response = send(body(request));
        long ms = (System.nanoTime() - started) / 1_000_000;
        if (response.statusCode() / 100 != 2) {
            log.warn("Word card AI call for \"{}\" ({}): HTTP {} after {} ms", request.word(), properties.model(),
                    response.statusCode(), ms);
            throw new AiUnavailableException("the AI answered HTTP " + response.statusCode(), null);
        }
        CardAnswer answer = parse(response.body());
        log.info("Word card AI call for \"{}\" ({}): {} in {} ms{}", request.word(), properties.model(),
                answer.getClass().getSimpleName(), ms, usage(response.body()));
        return answer;
    }

    private String body(CardRequest request) {
        ObjectNode body = CardJson.JSON.createObjectNode();
        body.put("model", properties.model());
        body.put("temperature", 0.3);
        body.put("max_tokens", 1500);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", CardPrompt.SYSTEM);
        messages.addObject().put("role", "user").put("content", CardPrompt.user(request));
        return CardJson.JSON.writeValueAsString(body);
    }

    private HttpResponse<String> send(String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(properties.timeout())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .header("X-Title", "Orbiko")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AiUnavailableException("the AI took too long", e);
        } catch (IOException e) {
            throw new AiUnavailableException("the AI couldn't be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("interrupted", e);
        }
    }

    /** The model's text → a card, "not a word", or unreadable. */
    static CardAnswer parse(String responseBody) {
        String text;
        try {
            text = CardJson.JSON.readTree(responseBody).path("choices").path(0).path("message").path("content").asString("");
        } catch (RuntimeException e) {
            return new CardAnswer.Unreadable("The answer was not a chat completion.");
        }
        String json = text.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode card;
        try {
            card = CardJson.JSON.readTree(json);
        } catch (RuntimeException e) {
            return new CardAnswer.Unreadable("Return only one JSON object.");
        }
        if (!card.isObject()) {
            return new CardAnswer.Unreadable("Return only one JSON object.");
        }
        String status = CardJson.text(card, "status");
        if ("NOT_A_WORD".equals(status)) {
            return new CardAnswer.NotAWord(CardJson.text(card, "reason"));
        }
        if (!"OK".equals(status)) {
            return new CardAnswer.Unreadable("status: use \"OK\" or \"NOT_A_WORD\".");
        }
        List<String> forms = new ArrayList<>(CardJson.strings(card.path("forms")));
        return new CardAnswer.Card(CardJson.text(card, "lemma"), forms, CardJson.read(card));
    }

    private static String usage(String responseBody) {
        try {
            JsonNode usage = CardJson.JSON.readTree(responseBody).path("usage");
            return usage.isObject() ? " (" + usage.path("total_tokens").asInt() + " tokens)" : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
