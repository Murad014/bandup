/* =========================================================================
   core.js — pure, DOM-free logic for the Reading Practice feature.
   Kept separate so it can be unit-tested without a browser/DOM.
   ========================================================================= */

/** Format a seconds value as m:ss, prefixing "+" once in overtime (negative). */
export function formatClock(remaining) {
    const neg = remaining < 0;
    const abs = Math.abs(remaining);
    const m = Math.floor(abs / 60);
    const s = String(abs % 60).padStart(2, "0");
    return (neg ? "+" : "") + m + ":" + s;
}

/** True when the remaining time is at/under the low-time threshold. */
export function isLowTime(remaining, threshold) {
    return remaining <= threshold;
}

/** Normalise a free-text answer for comparison (trim + lowercase). */
export function normalizeAnswer(value) {
    return String(value == null ? "" : value).trim().toLowerCase();
}

/**
 * Grade a single answer against an answer-key entry.
 * key.answer may be a string (TFNG) or an array of accepted strings (FILL).
 */
export function gradeAnswer(key, given) {
    if (given == null || String(given).trim() === "") return false;
    if (Array.isArray(key.answer)) {
        return key.answer.map(normalizeAnswer).includes(normalizeAnswer(given));
    }
    return normalizeAnswer(given) === normalizeAnswer(key.answer);
}

/** The answer to display to the learner (first accepted value for FILL). */
export function displayAnswer(key) {
    return Array.isArray(key.answer) ? key.answer[0] : key.answer;
}

/**
 * Grade a full attempt. This is the stub server's grading logic; the real
 * backend will reproduce it. Returns the shape the UI renders from.
 *
 * @param answerKey map of questionNumber -> { answer, explanation, evidenceId }
 * @param answers   array of { n, value }
 */
export function gradeAttempt(answerKey, answers) {
    const byN = new Map((answers || []).map((a) => [a.n, a.value]));
    const numbers = Object.keys(answerKey).map(Number).sort((a, b) => a - b);

    let score = 0;
    const results = numbers.map((n) => {
        const key = answerKey[n];
        const given = byN.has(n) ? byN.get(n) : "";
        const correct = gradeAnswer(key, given);
        if (correct) score += 1;
        return {
            n,
            correct,
            given,
            answer: displayAnswer(key),
            explanation: key.explanation,
            evidenceId: key.evidenceId,
        };
    });

    const total = numbers.length;
    const percent = total ? Math.round((score / total) * 100) : 0;
    return { score, total, percent, results };
}
