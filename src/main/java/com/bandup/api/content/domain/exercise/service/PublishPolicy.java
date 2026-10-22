package com.bandup.api.content.domain.exercise.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.bandup.api.content.domain.exercise.aggregate.Exercise;
import com.bandup.api.content.domain.exercise.entity.Question;
import com.bandup.api.content.domain.exercise.entity.QuestionGroup;
import com.bandup.api.content.domain.exercise.valueobject.Option;
import com.bandup.api.content.domain.exercise.valueobject.OptionKey;
import com.bandup.api.content.domain.exercise.valueobject.QuestionFamily;
import com.bandup.api.content.domain.exercise.valueobject.QuestionId;
import com.bandup.api.content.domain.exercise.valueobject.Skill;
import com.bandup.api.shared.domain.ddd.DomainService;
import com.bandup.api.shared.domain.error.Violation;
import com.bandup.api.shared.domain.numbering.NumberRange;

/**
 * What an exercise needs before learners may see it (backend requirement
 * 4.2): a passage for reading, at least one question, an instruction per
 * group, and per question a stem and a valid answer for its family:
 *
 * <ul>
 *   <li>TFNG / YNNG: exactly one value from the fixed set</li>
 *   <li>gap: at least one accepted answer, each within the word limit</li>
 *   <li>MCQ: two or more options, exactly one key that is one of them</li>
 *   <li>"Choose N": N set, more than N options, exactly N keys that exist</li>
 *   <li>matching: a bank of two or more, exactly one key from it, not reused
 *       by another question of the group unless reusable</li>
 *   <li>a diagram group has its picture</li>
 * </ul>
 *
 * Reports every violation at once, anchored to its field
 * ({@code questionGroups[1].questions[0].answer}). Draft saves don't apply it.
 */
@DomainService
public final class PublishPolicy {

    public List<Violation> check(Exercise exercise) {
        List<Violation> violations = new ArrayList<>();
        if (exercise.skill() == Skill.READING
                && exercise.passage().map(p -> p.html().isBlank()).orElse(true)) {
            violations.add(new Violation("passageHtml", "Add the passage text before publishing."));
        }
        if (exercise.groups().stream().allMatch(g -> g.questions().isEmpty())) {
            violations.add(new Violation("questions", "Add at least one question before publishing."));
        }
        Map<QuestionId, NumberRange> numbers = exercise.numbering();
        for (int g = 0; g < exercise.groups().size(); g++) {
            checkGroup(exercise.groups().get(g), "questionGroups[" + g + "]", numbers, violations);
        }
        return violations;
    }

    private static void checkGroup(QuestionGroup group, String field, Map<QuestionId, NumberRange> numbers,
            List<Violation> violations) {
        QuestionFamily family = group.type().family();
        if (group.questions().isEmpty()) {
            violations.add(new Violation(field, "This group has no questions."));
        }
        if (group.instruction().isBlank()) {
            violations.add(new Violation(field + ".instruction", "Write the instruction."));
        }
        if (group.type().needsImage() && group.media().isEmpty()) {
            violations.add(new Violation(field + ".media", "A diagram question needs its picture."));
        }
        if (family == QuestionFamily.MC_MULTI && group.chooseCount().isEmpty()) {
            violations.add(new Violation(field + ".chooseCount", "Say how many answers to choose."));
        }
        if (family == QuestionFamily.MATCH && group.bank().size() < 2) {
            violations.add(new Violation(field + ".options", "Add at least two options to choose from."));
        }
        Map<String, String> usedBy = new HashMap<>();
        for (int q = 0; q < group.questions().size(); q++) {
            Question question = group.questions().get(q);
            String questionField = field + ".questions[" + q + "]";
            String label = "Q" + numbers.get(question.id());
            if (question.stem().isBlank()) {
                violations.add(new Violation(questionField + ".stem", label + " has no text."));
            }
            List<String> answer = question.answer().values();
            String answerField = questionField + ".answer";
            switch (family) {
                case CHOICE_FIXED -> {
                    List<String> choices = group.type().fixedChoices();
                    if (answer.size() != 1 || !choices.contains(answer.getFirst())) {
                        violations.add(new Violation(answerField, label + " must be one of " + String.join(" / ", choices) + "."));
                    }
                }
                case GAP -> {
                    if (answer.isEmpty()) {
                        violations.add(new Violation(answerField, label + " needs at least one accepted answer."));
                    }
                    group.wordLimit().ifPresent(limit -> answer.stream().filter(a -> !limit.allows(a)).forEach(a ->
                            violations.add(new Violation(answerField, label + ": '" + a + "' is longer than the word limit."))));
                }
                case MC_SINGLE -> {
                    if (question.options().size() < 2) {
                        violations.add(new Violation(questionField + ".options", label + " needs at least two options."));
                    }
                    if (answer.size() != 1 || !keys(question.options()).contains(answer.getFirst())) {
                        violations.add(new Violation(answerField, label + " must select one of its options."));
                    }
                }
                case MC_MULTI -> group.chooseCount().ifPresent(n -> {
                    if (question.options().size() <= n) {
                        violations.add(new Violation(questionField + ".options", label + " needs more than " + n + " options."));
                    }
                    if (answer.size() != n) {
                        violations.add(new Violation(answerField, label + " must select exactly " + n + " options."));
                    }
                    if (!keys(question.options()).containsAll(answer)) {
                        violations.add(new Violation(answerField, label + " selects an option that doesn't exist."));
                    }
                });
                case MATCH -> {
                    if (answer.size() != 1 || !keys(group.bank()).contains(answer.getFirst())) {
                        violations.add(new Violation(answerField, label + " must match one of the group's options."));
                    } else if (!group.optionsReusable()) {
                        String earlier = usedBy.putIfAbsent(answer.getFirst(), label);
                        if (earlier != null) {
                            violations.add(new Violation(answerField, label + " uses '" + answer.getFirst()
                                    + "', already used by " + earlier + "; each option can be used once."));
                        }
                    }
                }
            }
        }
    }

    private static Set<String> keys(List<Option> options) {
        return options.stream().map(Option::key).map(OptionKey::value).collect(Collectors.toSet());
    }
}
