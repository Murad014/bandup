/* =========================================================================
   questionTypes.js — pluggable catalog of IELTS Reading question types.
   Each type declares a "family" that drives how it's authored, answered, and
   validated. Adding a type is a data change here; the editor and validator are
   generic over the families.

   Families:
     choiceFixed  one answer from a fixed set (TFNG, YNNG)        stem: statement
     gap          free-text answer(s) (completion / short answer) stem: prepost|prompt
     mcSingle     multiple choice, one answer (per-question opts)  stem: question
     mcMulti      multiple choice, several answers                 stem: question
     match        pick from a GROUP-level option bank (matching)   stem: prompt
   ========================================================================= */

export const QUESTION_TYPES = {
    TFNG:  { label: "True / False / Not Given",            family: "choiceFixed", stem: "statement", choices: ["TRUE", "FALSE", "NOT GIVEN"] },
    YNNG:  { label: "Yes / No / Not Given",                family: "choiceFixed", stem: "statement", choices: ["YES", "NO", "NOT GIVEN"] },
    MCQ:   { label: "Multiple choice (one answer)",        family: "mcSingle",    stem: "question" },
    MCMA:  { label: "Multiple choice (several answers)",   family: "mcMulti",     stem: "question" },
    FILL:  { label: "Sentence completion (gap)",           family: "gap",         stem: "prepost" },
    SUMMARY: { label: "Summary / note / table completion", family: "gap",        stem: "prepost" },
    SHORT: { label: "Short answer",                        family: "gap",         stem: "prompt" },
    MATCH_HEADINGS: { label: "Matching headings",          family: "match",       stem: "prompt", optionsLabel: "Headings" },
    MATCH_INFO:     { label: "Matching information",       family: "match",       stem: "prompt", optionsLabel: "Paragraphs" },
    MATCH_FEATURES: { label: "Matching features",          family: "match",       stem: "prompt", optionsLabel: "Features" },
    MATCH_ENDINGS:  { label: "Matching sentence endings",  family: "match",       stem: "prompt", optionsLabel: "Endings" },
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

/** Sequential option keys: A, B, C, ... */
export function optionKeyAt(index) {
    return String.fromCharCode(65 + index);
}
