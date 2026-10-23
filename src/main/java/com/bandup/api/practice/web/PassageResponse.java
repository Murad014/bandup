package com.bandup.api.practice.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.bandup.api.practice.application.PassageView;

/**
 * The learner passage JSON ({@code reading-practice-ui} design §4.1, extended
 * per {@code database} design §5.1). Empty or missing values are left out.
 *
 * @param id         the slug (the UI's session id)
 * @param stageLabel the stage title in the help language ("Mərhələ 1"); {@code position}
 *                   is sent apart so the UI can add its own wording ("Mini passage 4")
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record PassageResponse(String id, String language, String direction, String stageLabel, Integer position,
        String title, Integer durationSeconds, Integer lowTimeThresholdSeconds, String passageHtml,
        Map<String, String> glosses, List<Group> questionGroups, String coverUrl, List<String> topics) {

    static PassageResponse of(PassageView view) {
        return new PassageResponse(view.slug(), view.language(), view.direction(), view.stageTitle().orElse(null),
                view.position().orElse(null), view.title(), view.durationSeconds().orElse(null),
                view.lowTimeThresholdSeconds().orElse(null), view.passageHtml(), view.glosses(),
                view.groups().stream().map(Group::of).toList(), view.coverUrl(), view.topics());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Group(String type, String heading, String instruction, String instructionTranslated, Integer maxWords,
            boolean numbersAllowed, Integer chooseCount, boolean optionsReusable, List<Option> options,
            List<Question> questions) {

        static Group of(PassageView.Group group) {
            return new Group(group.type(), heading(group.firstNumber(), group.lastNumber()), group.instruction(),
                    group.instructionTranslated().orElse(null), group.maxWords().orElse(null), group.numbersAllowed(),
                    group.chooseCount().orElse(null), group.optionsReusable(), toOptions(group.options()),
                    group.questions().stream().map(Question::of).toList());
        }

        /** "Question 9" or "Questions 1–3". */
        static String heading(int first, int last) {
            return first == last ? "Question " + first : "Questions " + first + "–" + last;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Question(int n, Integer nTo, String type, String text, String pre, String post, List<Option> options) {

        static Question of(PassageView.Question question) {
            return new Question(question.n(), question.nTo().orElse(null), question.type(),
                    question.text().orElse(null), question.pre().orElse(null), question.post().orElse(null),
                    toOptions(question.options()));
        }
    }

    record Option(String key, String label) {
    }

    private static List<Option> toOptions(List<PassageView.Option> options) {
        return options.stream().map(o -> new Option(o.key(), o.label())).toList();
    }
}
