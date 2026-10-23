/* =========================================================================
   validate.js — publish hints for the editor. Same rules and messages as the
   server's PublishPolicy (backend design §8.1), so authors see problems
   while they write. Hints only: the server decides on save and publish.

   publishHints(exercise) → [{field, message}] for the editor's model
   (the AdminExerciseView shape: questionGroups[].questions[]).
   ========================================================================= */

import { familyOf, typeDef } from "./questionTypes.js";

const NUMBER = /^\d+([.,:/-]\d+)*$/;

function words(answer, numbersAllowed) {
    return String(answer).trim().split(/\s+/).filter((w) => w && !(numbersAllowed && NUMBER.test(w))).length;
}

function blankHtml(html) {
    return String(html || "").replace(/<[^>]*>/g, "").trim() === "";
}

/** "Q7" / "Q7–8" per question, numbered like the server (a "Choose N" question takes N numbers). */
export function questionLabels(exercise) {
    const labels = new Map();
    let next = 1;
    (exercise.questionGroups || []).forEach((g) => {
        const points = familyOf(g.type) === "mcMulti" && Number(g.chooseCount) > 0 ? Number(g.chooseCount) : 1;
        (g.questions || []).forEach((q) => {
            labels.set(q, "Q" + (points > 1 ? next + "–" + (next + points - 1) : next));
            next += points;
        });
    });
    return labels;
}

export function publishHints(exercise) {
    const hints = [];
    const add = (field, message) => hints.push({ field, message });
    const groups = exercise.questionGroups || [];
    if ((exercise.skill || "READING") === "READING" && blankHtml(exercise.passageHtml)) {
        add("passageHtml", "Add the passage text before publishing.");
    }
    if (groups.every((g) => !(g.questions || []).length)) {
        add("questions", "Add at least one question before publishing.");
    }
    const labels = questionLabels(exercise);
    groups.forEach((g, gi) => {
        const field = "questionGroups[" + gi + "]";
        const family = familyOf(g.type);
        const def = typeDef(g.type);
        const questions = g.questions || [];
        const bank = (g.options || []).map((o) => o.key);
        const n = Number(g.chooseCount) || 0;
        if (!questions.length) add(field, "This group has no questions.");
        if (blankHtml(g.instruction)) add(field + ".instruction", "Write the instruction.");
        if (g.type === "DIAGRAM_LABEL" && !g.mediaId) add(field + ".media", "A diagram question needs its picture.");
        if (family === "mcMulti" && !n) add(field + ".chooseCount", "Say how many answers to choose.");
        if (family === "match" && bank.length < 2) add(field + ".options", "Add at least two options to choose from.");
        const usedBy = new Map();
        questions.forEach((q, qi) => {
            const qf = field + ".questions[" + qi + "]";
            const label = labels.get(q);
            const answer = (q.answer || []).filter((a) => String(a).trim() !== "");
            const own = (q.options || []).map((o) => o.key);
            const stemBlank = q.pre != null ? !String(q.pre).trim() && !String(q.post || "").trim() : !String(q.text || "").trim();
            if (stemBlank) add(qf + ".stem", label + " has no text.");
            if (family === "choiceFixed") {
                if (answer.length !== 1 || !def.choices.includes(answer[0])) {
                    add(qf + ".answer", label + " must be one of " + def.choices.join(" / ") + ".");
                }
            } else if (family === "gap") {
                if (!answer.length) add(qf + ".answer", label + " needs at least one accepted answer.");
                if (Number(g.maxWords) > 0) {
                    answer.filter((a) => words(a, g.numbersAllowed) > Number(g.maxWords))
                        .forEach((a) => add(qf + ".answer", label + ": '" + a + "' is longer than the word limit."));
                }
            } else if (family === "mcSingle") {
                if (own.length < 2) add(qf + ".options", label + " needs at least two options.");
                if (answer.length !== 1 || !own.includes(answer[0])) add(qf + ".answer", label + " must select one of its options.");
            } else if (family === "mcMulti" && n) {
                if (own.length <= n) add(qf + ".options", label + " needs more than " + n + " options.");
                if (answer.length !== n) add(qf + ".answer", label + " must select exactly " + n + " options.");
                if (!answer.every((a) => own.includes(a))) add(qf + ".answer", label + " selects an option that doesn't exist.");
            } else if (family === "match") {
                if (answer.length !== 1 || !bank.includes(answer[0])) {
                    add(qf + ".answer", label + " must match one of the group's options.");
                } else if (!g.optionsReusable) {
                    const earlier = usedBy.get(answer[0]);
                    if (earlier) {
                        add(qf + ".answer", label + " uses '" + answer[0] + "', already used by " + earlier + "; each option can be used once.");
                    } else {
                        usedBy.set(answer[0], label);
                    }
                }
            }
        });
    });
    return hints;
}
