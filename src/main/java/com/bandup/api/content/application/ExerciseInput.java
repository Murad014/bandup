package com.bandup.api.content.application;

import java.util.List;
import java.util.Map;

/**
 * What the admin editor sends to create or update an exercise (design §8.4).
 * Read-only fields of the view ({@code n}, {@code heading}, gloss
 * {@code surface}, {@code status}, {@code revision}) are simply not here.
 *
 * @param id             the slug; used only when creating
 * @param version        the version the editor loaded; required when updating
 * @param skill          create only (default READING)
 * @param targetLanguage create only (default {@code en})
 * @param glosses        lemma and meanings per gloss key; the words themselves come from {@code passageHtml}
 * @param coverMediaId   the cover image (the id an upload returned), or null for the topic's illustration
 */
public record ExerciseInput(String id, Long version, String skill, String targetLanguage, String title, String format,
        String level, Integer durationSeconds, Integer lowTimeThresholdSeconds, Long stageId, Integer position,
        List<Long> topicIds, String passageHtml, Map<String, GlossInput> glosses, List<GroupInput> questionGroups,
        Long coverMediaId) {

    /** @param meanings language → meaning */
    public record GlossInput(String lemma, Map<String, TextInput> meanings) {
    }

    /**
     * A piece of help text in one language.
     *
     * @param source   HUMAN (default) or MACHINE
     * @param reviewed default false
     */
    public record TextInput(String text, String source, Boolean reviewed) {
    }

    /** @param id null for a new group */
    public record GroupInput(Long id, String type, String instruction, Map<String, String> instructionTranslations,
            Integer chooseCount, Integer maxWords, Boolean numbersAllowed, Boolean optionsReusable, Long mediaId,
            List<OptionInput> options, List<QuestionInput> questions) {
    }

    public record OptionInput(String key, String label) {
    }

    /**
     * @param id           null for a new question
     * @param pre          set for a gap stem ({@code pre ___ post}); otherwise {@code text}
     * @param answer       accepted values / required keys, in order
     * @param explanations language → explanation HTML ({@code text})
     */
    public record QuestionInput(Long id, String text, String pre, String post, String evidenceId,
            List<OptionInput> options, List<String> answer, Map<String, TextInput> explanations) {
    }
}
