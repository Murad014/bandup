/* =========================================================================
   questionTypes.js — the IELTS Reading question types (database seed
   `question_type`). The family decides how a question is written, answered
   and checked (backend design §8.1).

     choiceFixed  one value from a fixed set (TFNG, YNNG)          stem: text
     gap          accepted answers, word limit                     stem: gap (pre/post) or text
     mcSingle     own options, one key                             stem: text
     mcMulti      own options, "Choose N" keys                     stem: text
     match        one key from the group's option bank             stem: text (SUMMARY_BOX: gap)
   ========================================================================= */

export const QUESTION_TYPES = {
    TFNG: { label: "True / False / Not Given", family: "choiceFixed", stem: "text", choices: ["TRUE", "FALSE", "NOT GIVEN"] },
    YNNG: { label: "Yes / No / Not Given", family: "choiceFixed", stem: "text", choices: ["YES", "NO", "NOT GIVEN"] },
    MCQ: { label: "Multiple choice (one answer)", family: "mcSingle", stem: "text" },
    MCMA: { label: "Multiple choice (choose N)", family: "mcMulti", stem: "text" },
    FILL: { label: "Sentence completion (gap)", family: "gap", stem: "gap" },
    SUMMARY: { label: "Summary / note / table completion", family: "gap", stem: "gap" },
    SHORT: { label: "Short answer", family: "gap", stem: "text" },
    MATCH_HEADINGS: { label: "Matching headings", family: "match", stem: "text", optionsLabel: "Headings" },
    MATCH_INFO: { label: "Matching information", family: "match", stem: "text", optionsLabel: "Paragraphs" },
    MATCH_FEATURES: { label: "Matching features", family: "match", stem: "text", optionsLabel: "Features" },
    MATCH_ENDINGS: { label: "Matching sentence endings", family: "match", stem: "text", optionsLabel: "Endings" },
    SUMMARY_BOX: { label: "Summary from a box of words", family: "match", stem: "gap", optionsLabel: "Box" },
    DIAGRAM_LABEL: { label: "Diagram labelling — needs a picture (coming with media)", family: "gap", stem: "text", disabled: true },
};

export function typeDef(key) {
    return QUESTION_TYPES[key] || QUESTION_TYPES.TFNG;
}
export function familyOf(key) {
    return typeDef(key).family;
}
export function orderedTypeKeys() {
    return Object.keys(QUESTION_TYPES);
}
export function usesGroupOptions(key) {
    return familyOf(key) === "match";
}
export function usesPerQuestionOptions(key) {
    const f = familyOf(key);
    return f === "mcSingle" || f === "mcMulti";
}

/** Option keys: A, B, C… for choices; i, ii, iii… for headings. */
export function optionKeyAt(index, roman) {
    if (!roman) return String.fromCharCode(65 + index);
    const numerals = ["i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii"];
    return numerals[index] || String(index + 1);
}

/** The help languages offered for meanings, translations and explanations. */
export const HELP_LANGUAGES = [
    { code: "az", name: "Azərbaycanca" },
    { code: "en", name: "English" },
];
