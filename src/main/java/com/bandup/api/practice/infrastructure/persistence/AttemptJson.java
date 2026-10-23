package com.bandup.api.practice.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;

import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON stored in {@code attempt_answer}: a given text is a JSON string
 * ({@code "FALSE"}), given keys a JSON array ({@code ["A","C"]}); the
 * expected snapshot is always an array of the accepted values.
 */
@UtilityClass
final class AttemptJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static String given(GivenAnswer given) {
        return switch (given) {
            case GivenAnswer.Text text -> JSON.writeValueAsString(text.value());
            case GivenAnswer.Keys keys -> JSON.writeValueAsString(keys.keys());
        };
    }

    static GivenAnswer given(String json) {
        JsonNode node = JSON.readTree(json);
        return node.isArray() ? new GivenAnswer.Keys(strings(node)) : new GivenAnswer.Text(node.asString());
    }

    static String expected(List<String> expected) {
        return JSON.writeValueAsString(expected);
    }

    static List<String> expected(String json) {
        return strings(JSON.readTree(json));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return values;
    }
}
