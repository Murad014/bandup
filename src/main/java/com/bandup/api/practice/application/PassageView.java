package com.bandup.api.practice.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read model: a reading exercise as the learner sees it before submitting
 * (backend requirement 2, {@code database} design §5.1). Holds no answers,
 * explanations or evidence links by construction.
 *
 * @param slug                    the public id ({@code /reading/{slug}})
 * @param language                the help language the text was resolved for (chain's first)
 * @param direction               that language's writing direction, {@code ltr} or {@code rtl}
 * @param stageTitle              the stage's title in the help language, if the exercise is in a stage
 * @param position                the exercise's position in its stage, if any
 * @param passageHtml             sanitized HTML with {@code data-g} gloss keys and {@code ev} spans
 * @param glosses                 gloss key → meaning; keys with no meaning in the chain are left out
 * @param coverUrl                the cover image, or null
 * @param topics                  topic slugs (the page's fallback illustration uses the first)
 */
public record PassageView(
        String slug,
        String language,
        String direction,
        String title,
        Optional<Integer> durationSeconds,
        Optional<Integer> lowTimeThresholdSeconds,
        Optional<String> stageTitle,
        Optional<Integer> position,
        String passageHtml,
        Map<String, String> glosses,
        List<Group> groups,
        String coverUrl,
        List<String> topics) {

    public PassageView {
        glosses = Map.copyOf(glosses);
        groups = List.copyOf(groups);
        topics = List.copyOf(topics);
    }

    /**
     * One question group.
     *
     * @param type                  question type code, e.g. {@code TFNG}
     * @param firstNumber           first question number of the group
     * @param lastNumber            last question number ("Questions 1–3")
     * @param instruction           the official instruction (target language, HTML)
     * @param instructionTranslated helper translation in the help language, if any
     * @param options               the group's option bank (match family), else empty
     */
    public record Group(
            String type,
            int firstNumber,
            int lastNumber,
            String instruction,
            Optional<String> instructionTranslated,
            Optional<Integer> maxWords,
            boolean numbersAllowed,
            Optional<Integer> chooseCount,
            boolean optionsReusable,
            List<Option> options,
            List<Question> questions) {

        public Group {
            options = List.copyOf(options);
            questions = List.copyOf(questions);
        }
    }

    /**
     * One question. A gap has {@code pre}/{@code post}, others {@code text}.
     *
     * @param n       its first number
     * @param nTo     its last number when it takes several ("Choose TWO": 7–8)
     * @param options its own options (MCQ / "Choose N"), else empty
     */
    public record Question(
            int n,
            Optional<Integer> nTo,
            String type,
            Optional<String> text,
            Optional<String> pre,
            Optional<String> post,
            List<Option> options) {

        public Question {
            options = List.copyOf(options);
        }
    }

    public record Option(String key, String label) {
    }
}
