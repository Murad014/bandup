package com.bandup.api.practice.domain.attempt.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.bandup.api.practice.domain.attempt.valueobject.GivenAnswer;
import com.bandup.api.practice.domain.attempt.valueobject.GradingRule;
import com.bandup.api.practice.domain.attempt.valueobject.Score;
import com.bandup.api.shared.domain.ddd.DomainService;

/**
 * The IELTS scoring rules (backend requirement 3.2, database design §4.6):
 *
 * <ul>
 *   <li>Matching ignores case and extra spaces ("  Grey   Wolves " = "grey wolves").</li>
 *   <li>Gap: one of the accepted values <b>and</b> within the word limit; with
 *       "AND/OR A NUMBER", numbers don't count as words.</li>
 *   <li>"Choose N": 1 point per correct key, up to N; more than N keys = 0.</li>
 *   <li>Every other family: 1 point if the value/key matches.</li>
 *   <li>A blank answer scores 0.</li>
 * </ul>
 *
 * The client never sends a grade; this is the only place one is made.
 */
@DomainService
public final class Grader {

    public Score score(GradingRule rule, GivenAnswer given) {
        int possible = rule.points();
        if (given.isBlank()) {
            return Score.zero(possible);
        }
        return switch (rule.family()) {
            case MC_MULTI -> new Score(correctKeys(rule, given), possible);
            case GAP -> new Score(matches(rule, given.asText()) && withinLimit(rule, given.asText()) ? 1 : 0, possible);
            case CHOICE_FIXED, MC_SINGLE, MATCH -> new Score(matches(rule, given.asText()) ? 1 : 0, possible);
        };
    }

    private static int correctKeys(GradingRule rule, GivenAnswer given) {
        Set<String> keys = normalized(given.asKeys());
        if (keys.size() > rule.chooseCount()) {
            return 0; // ticking more boxes than asked scores nothing
        }
        Set<String> required = normalized(rule.accepted());
        return (int) keys.stream().filter(required::contains).count();
    }

    private static boolean matches(GradingRule rule, String given) {
        return normalized(rule.accepted()).contains(normalize(given));
    }

    private static boolean withinLimit(GradingRule rule, String given) {
        return rule.wordLimit().map(limit -> limit.allows(given)).orElse(true);
    }

    private static Set<String> normalized(List<String> values) {
        return values.stream().map(Grader::normalize).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
    }

    /** Trimmed, lower-case, inner whitespace collapsed to one space. */
    static String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
