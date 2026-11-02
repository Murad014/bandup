package com.bandup.api.grammar.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.experimental.UtilityClass;

import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.ItemId;
import com.bandup.api.grammar.domain.lesson.valueobject.LearnCard;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonBody;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonText;
import com.bandup.api.grammar.domain.lesson.valueobject.Topic;
import com.bandup.api.grammar.domain.lesson.valueobject.WorkedExample;
import com.bandup.api.shared.domain.language.LanguageCode;
import com.bandup.api.shared.domain.language.Translations;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The lesson body as stored in {@code grammar_lesson.content}: the shape of
 * {@code lesson-format.md}, with every text as a language object
 * ({@code {"en": …, "az": …}}). The position lives in its own column, so a
 * read body has none.
 */
@UtilityClass
final class LessonJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // ---------------------------------------------------------------- write

    static String write(LessonBody body) {
        ObjectNode root = JSON.createObjectNode();
        root.put("level", body.level().name());
        ObjectNode topic = root.putObject("topic");
        topic.put("key", body.topic().key());
        topic.set("label", text(body.topic().label()));
        root.set("title", text(body.title()));
        root.set("goal", text(body.goal()));
        root.put("minutes", body.minutes());
        ArrayNode learn = root.putArray("learn");
        body.learn().forEach(card -> learn.add(card(card)));
        ObjectNode worked = root.putObject("worked");
        worked.set("question", text(body.worked().question()));
        worked.set("steps", texts(body.worked().steps()));
        worked.put("answer", body.worked().answer());
        ArrayNode practice = root.putArray("practice");
        body.practice().forEach(item -> practice.add(item(item)));
        ArrayNode check = root.putArray("check");
        body.check().forEach(item -> check.add(item(item)));
        return JSON.writeValueAsString(root);
    }

    private static ObjectNode card(LearnCard card) {
        ObjectNode node = JSON.createObjectNode();
        node.put("icon", card.icon());
        node.set("label", text(card.label()));
        node.set("heading", text(card.heading()));
        card.bodyText().ifPresent(t -> node.set("body", text(t)));
        if (!card.steps().isEmpty()) {
            node.set("steps", texts(card.steps()));
        }
        if (!card.examples().isEmpty()) {
            ArrayNode examples = node.putArray("examples");
            card.examples().forEach(e -> {
                ObjectNode example = examples.addObject();
                example.put("ok", e.ok());
                example.set("text", text(e.text()));
            });
        }
        card.tipText().ifPresent(t -> node.set("tip", text(t)));
        return node;
    }

    private static ObjectNode item(Item item) {
        ObjectNode node = JSON.createObjectNode();
        node.put("id", item.id().value());
        node.put("type", item.code());
        node.set("prompt", text(item.prompt()));
        switch (item) {
            case Item.Choose choose -> {
                node.set("options", strings(choose.options()));
                node.put("answer", choose.answer());
                if (!choose.whys().isEmpty()) {
                    node.set("whys", texts(choose.whys()));
                }
            }
            case Item.Type type -> {
                node.put("sentence", type.sentence());
                node.set("accept", strings(type.accept()));
                if (type.show() != null) {
                    node.put("show", type.show());
                }
            }
            case Item.Fix fix -> {
                node.put("wrong", fix.wrong());
                node.set("accept", strings(fix.accept()));
                if (fix.show() != null) {
                    node.put("show", fix.show());
                }
            }
            case Item.Build build -> {
                node.set("tiles", strings(build.tiles()));
                node.put("answer", build.answer());
            }
        }
        node.set("explanation", text(item.explanation()));
        return node;
    }

    private static ObjectNode text(LessonText text) {
        ObjectNode node = JSON.createObjectNode();
        text.values().asMap().forEach((language, value) -> node.put(language.value(), value));
        return node;
    }

    private static ArrayNode texts(List<LessonText> texts) {
        ArrayNode array = JSON.createArrayNode();
        texts.forEach(t -> array.add(text(t)));
        return array;
    }

    private static ArrayNode strings(List<String> values) {
        ArrayNode array = JSON.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    // ----------------------------------------------------------------- read

    static LessonBody read(String json) {
        JsonNode root = JSON.readTree(json);
        JsonNode worked = root.get("worked");
        return new LessonBody(GrammarLevel.valueOf(root.get("level").asString()),
                new Topic(root.get("topic").get("key").asString(), text(root.get("topic").get("label"))),
                text(root.get("title")), text(root.get("goal")), root.get("minutes").asInt(),
                null, // the position lives in its own column
                list(root.get("learn"), LessonJson::card),
                new WorkedExample(text(worked.get("question")), list(worked.get("steps"), LessonJson::text),
                        worked.get("answer").asString()),
                list(root.get("practice"), LessonJson::item), list(root.get("check"), LessonJson::item));
    }

    private static LearnCard card(JsonNode node) {
        return new LearnCard(node.get("icon").asString(), text(node.get("label")), text(node.get("heading")),
                optionalText(node.get("body")), list(node.get("steps"), LessonJson::text),
                list(node.get("examples"), e -> new LearnCard.Example(e.get("ok").asBoolean(), text(e.get("text")))),
                optionalText(node.get("tip")));
    }

    private static Item item(JsonNode node) {
        ItemId id = new ItemId(node.get("id").asString());
        LessonText prompt = text(node.get("prompt"));
        LessonText explanation = text(node.get("explanation"));
        return switch (node.get("type").asString()) {
            case "choose" -> new Item.Choose(id, prompt, strings(node.get("options")), node.get("answer").asInt(),
                    list(node.get("whys"), LessonJson::text), explanation);
            case "type" -> new Item.Type(id, prompt, node.get("sentence").asString(), strings(node.get("accept")),
                    optionalString(node.get("show")), explanation);
            case "fix" -> new Item.Fix(id, prompt, node.get("wrong").asString(), strings(node.get("accept")),
                    optionalString(node.get("show")), explanation);
            case "build" -> new Item.Build(id, prompt, strings(node.get("tiles")), node.get("answer").asString(),
                    explanation);
            default -> throw new IllegalArgumentException("Unknown item type in stored lesson: " + node.get("type"));
        };
    }

    private static LessonText text(JsonNode node) {
        Map<LanguageCode, String> values = new LinkedHashMap<>();
        node.properties().forEach(entry -> values.put(LanguageCode.of(entry.getKey()), entry.getValue().asString()));
        return new LessonText(Translations.of(values));
    }

    private static LessonText optionalText(JsonNode node) {
        return node == null || node.isNull() ? null : text(node);
    }

    private static String optionalString(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static List<String> strings(JsonNode array) {
        return list(array, JsonNode::asString);
    }

    private static <T> List<T> list(JsonNode array, Function<JsonNode, T> each) {
        List<T> values = new ArrayList<>();
        if (array != null) {
            array.forEach(node -> values.add(each.apply(node)));
        }
        return values;
    }

    // ----------------------------------------------------- run's mastered set

    static String ids(Collection<ItemId> ids) {
        return JSON.writeValueAsString(strings(ids.stream().map(ItemId::value).toList()));
    }

    static List<ItemId> ids(String json) {
        return strings(JSON.readTree(json)).stream().map(ItemId::new).toList();
    }
}
