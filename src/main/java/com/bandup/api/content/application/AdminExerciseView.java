package com.bandup.api.content.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The full editable exercise for the admin editor, answers included
 * (design §8.4). Null means "not set".
 */
public record AdminExerciseView(String id, long version, String status, int revision, String skill,
        String targetLanguage, String title, String format, String level, Integer durationSeconds,
        Integer lowTimeThresholdSeconds, Long stageId, Integer position, List<Long> topicIds, Instant publishedAt,
        String passageHtml, Integer wordCount, Map<String, GlossView> glosses, List<GroupView> questionGroups) {

    public record GlossView(String surface, String lemma, Map<String, TextView> meanings) {
    }

    public record TextView(String text, String source, boolean reviewed) {
    }

    public record GroupView(long id, String type, String heading, String instruction,
            Map<String, String> instructionTranslations, Integer chooseCount, Integer maxWords, boolean numbersAllowed,
            boolean optionsReusable, Long mediaId, List<OptionView> options, List<QuestionView> questions) {
    }

    public record OptionView(String key, String label) {
    }

    /** @param n first number; {@code nTo} the last one when it takes several */
    public record QuestionView(long id, int n, Integer nTo, String text, String pre, String post, String evidenceId,
            List<OptionView> options, List<String> answer, Map<String, TextView> explanations) {
    }
}
