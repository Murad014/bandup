package com.bandup.api.vocabulary.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;

import com.bandup.api.vocabulary.domain.word.valueobject.CardContent;
import com.bandup.api.vocabulary.domain.word.valueobject.GapTask;
import com.bandup.api.vocabulary.domain.word.valueobject.Meaning;
import com.bandup.api.vocabulary.domain.word.valueobject.PairTask;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A card's content as stored in {@code vocab_word.content} and as the AI
 * returns it (card-format.md, without status, lemma and forms). Reading is
 * lenient (missing values become empty), so {@code CardPolicy} can report
 * every problem with its path.
 */
@UtilityClass
public final class CardJson {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    public static String write(CardContent c) {
        return JSON.writeValueAsString(node(c));
    }

    public static ObjectNode node(CardContent c) {
        ObjectNode root = JSON.createObjectNode();
        root.put("pos", c.pos());
        root.put("level", c.level());
        root.put("ipa", c.ipa());
        ObjectNode meaning = root.putObject("meaning");
        meaning.put("en", c.meaning().en());
        meaning.put("az", c.meaning().az());
        strings(root.putArray("examples"), c.examples());
        strings(root.putArray("collocations"), c.collocations());
        strings(root.putArray("family"), c.family());
        ObjectNode gap = root.putObject("gap");
        gap.put("sentence", c.gap().sentence());
        gap.put("answer", c.gap().answer());
        ObjectNode pair = root.putObject("pair");
        pair.put("sentence", c.pair().sentence());
        strings(pair.putArray("options"), c.pair().options());
        pair.put("answer", c.pair().answer());
        strings(root.putArray("wrongMeanings"), c.wrongMeanings());
        return root;
    }

    public static CardContent read(String json) {
        return read(JSON.readTree(json));
    }

    public static CardContent read(JsonNode n) {
        JsonNode meaning = n.path("meaning");
        JsonNode gap = n.path("gap");
        JsonNode pair = n.path("pair");
        return new CardContent(text(n, "pos"), text(n, "level"), text(n, "ipa"),
                new Meaning(text(meaning, "en"), text(meaning, "az")), strings(n.path("examples")),
                strings(n.path("collocations")), strings(n.path("family")),
                new GapTask(text(gap, "sentence"), text(gap, "answer")),
                new PairTask(text(pair, "sentence"), strings(pair.path("options")),
                        pair.path("answer").isIntegralNumber() ? pair.path("answer").asInt() : -1),
                strings(n.path("wrongMeanings")));
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString().strip() : "";
    }

    public static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode value : array) {
                values.add(value.isString() ? value.asString().strip() : "");
            }
        }
        return values;
    }

    private static void strings(ArrayNode array, List<String> values) {
        values.forEach(array::add);
    }
}
