/* =========================================================================
   core.js — small DOM-free helpers for the reading page.
   (Grading lives on the server only; the browser never sees answers before
   submitting.)
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

/** How a question type is answered (database design §4.6). */
export const FAMILY = {
    TFNG: "fixed", YNNG: "fixed",
    MCQ: "single", MCMA: "multi",
    FILL: "gap", SUMMARY: "gap", SHORT: "gap", DIAGRAM_LABEL: "gap",
    MATCH_HEADINGS: "match", MATCH_INFO: "match", MATCH_FEATURES: "match", MATCH_ENDINGS: "match",
    SUMMARY_BOX: "match",
};

export const FIXED_CHOICES = {
    TFNG: ["TRUE", "FALSE", "NOT GIVEN"],
    YNNG: ["YES", "NO", "NOT GIVEN"],
};

const WORDS = ["ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX"];

/** "NO MORE THAN TWO WORDS AND/OR A NUMBER" from a group's word limit; "" without one. */
export function wordLimitHint(maxWords, numbersAllowed) {
    if (!maxWords) return "";
    const count = WORDS[maxWords] || String(maxWords);
    const base = maxWords === 1 ? "ONE WORD ONLY" : "NO MORE THAN " + count + " WORDS";
    return numbersAllowed ? base.replace(" ONLY", "") + " AND/OR A NUMBER" : base;
}

/** "7" or "7–8". */
export function numberLabel(q) {
    return q.nTo ? q.n + "–" + q.nTo : String(q.n);
}

/** The help languages offered in the picker (enabled support languages). */
export const HELP_LANGUAGES = [
    { code: "az", name: "Azərbaycanca" },
    { code: "en", name: "English" },
];
