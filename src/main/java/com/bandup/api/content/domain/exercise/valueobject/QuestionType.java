package com.bandup.api.content.domain.exercise.valueobject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * A question type ({@code question_type} reference data): its code and
 * family. Knows the fixed answer set of TFNG / YNNG and which type needs an
 * image.
 */
@ValueObject
public record QuestionType(String code, QuestionFamily family) {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z_]{0,31}");
    private static final Map<String, List<String>> FIXED_CHOICES = Map.of(
            "TFNG", List.of("TRUE", "FALSE", "NOT GIVEN"),
            "YNNG", List.of("YES", "NO", "NOT GIVEN"));

    public QuestionType {
        Objects.requireNonNull(family, "family");
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid question type code " + code);
        }
    }

    /** The allowed answers of a CHOICE_FIXED type (empty for other types). */
    public List<String> fixedChoices() {
        return FIXED_CHOICES.getOrDefault(code, List.of());
    }

    /** DIAGRAM_LABEL questions are answered on a picture, so the group needs one. */
    public boolean needsImage() {
        return code.equals("DIAGRAM_LABEL");
    }
}
