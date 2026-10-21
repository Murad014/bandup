/* =========================================================================
   validate.js — pure validation for an admin session (design §6), now
   catalog-driven so every IELTS question family is covered.
   Draft save relaxes content rules; publish enforces all.
   Returns { errors: [{field,message}], warnings: [...], ok }.
   ========================================================================= */

import { typeDef, familyOf } from "./questionTypes.js";

/** Extract evidence ids (eN) present in the passage HTML. */
export function evidenceIdsIn(passageHtml) {
    const ids = [];
    const re = /class="ev"[^>]*\bid="([^"]+)"|id="([^"]+)"[^>]*class="ev"/g;
    let m;
    while ((m = re.exec(String(passageHtml || ""))) !== null) ids.push(m[1] || m[2]);
    return ids;
}

function nonEmptyList(value) {
    const list = Array.isArray(value) ? value : [value];
    return list.filter((v) => v != null && String(v).trim() !== "");
}

export function validate(session, options = {}) {
    const forPublish = options.forPublish === true;
    const errors = [];
    const warnings = [];
    const add = (field, message) => errors.push({ field, message });

    // Metadata (always).
    if (!session.title || !String(session.title).trim()) add("title", "Title is required.");
    if (!session.stageLabel || !String(session.stageLabel).trim()) add("stageLabel", "Stage label is required.");
    const dur = Number(session.durationSeconds);
    const low = Number(session.lowTimeThresholdSeconds);
    if (!(dur > 0)) add("durationSeconds", "Duration must be greater than 0.");
    if (!(low > 0)) add("lowTimeThresholdSeconds", "Low-time threshold must be greater than 0.");
    else if (dur > 0 && low > dur) add("lowTimeThresholdSeconds", "Low-time threshold cannot exceed the duration.");

    const groups = session.questionGroups || [];
    const answerByN = new Map((session.answerKey || []).map((a) => [a.n, a]));
    const evidenceIds = new Set(evidenceIdsIn(session.passageHtml));
    const allQuestions = [];
    groups.forEach((g) => (g.questions || []).forEach((q) => allQuestions.push(q)));

    if (forPublish) {
        if (allQuestions.length === 0) add("questions", "Add at least one question before publishing.");

        // Unique, contiguous numbering 1..N.
        const numbers = allQuestions.map((q) => q.n).sort((a, b) => a - b);
        numbers.forEach((n, i) => { if (n !== i + 1) add("questions", "Question numbers must be unique and contiguous (1..N)."); });

        groups.forEach((g) => {
            const fam = familyOf(g.type);
            const def = typeDef(g.type);
            const F = "group(" + g.type + ")";

            if (fam === "match") {
                const opts = nonEmptyList((g.options || []).map((o) => o.label));
                if ((g.options || []).length < 2 || opts.length < 2) add(F + ".options", (def.optionsLabel || "Options") + " need at least two labelled entries.");
            }

            (g.questions || []).forEach((q) => {
                const key = answerByN.get(q.n);
                const qf = "answerKey[" + q.n + "]";
                if (!key) { add(qf, "Question " + q.n + " has no answer."); return; }

                if (fam === "choiceFixed") {
                    if (!def.choices.includes(key.answer)) add(qf + ".answer", "Q" + q.n + " answer must be one of " + def.choices.join(" / ") + ".");
                } else if (fam === "gap") {
                    if (nonEmptyList(key.answer).length === 0) add(qf + ".answer", "Q" + q.n + " needs at least one accepted answer.");
                } else if (fam === "mcSingle") {
                    const keys = (q.options || []).map((o) => o.key);
                    if (keys.length < 2) add("questions", "Q" + q.n + " needs at least two options.");
                    if (!key.answer || !keys.includes(key.answer)) add(qf + ".answer", "Q" + q.n + " must select one of its options.");
                } else if (fam === "mcMulti") {
                    const keys = (q.options || []).map((o) => o.key);
                    const chosen = nonEmptyList(key.answer);
                    if (keys.length < 2) add("questions", "Q" + q.n + " needs at least two options.");
                    if (chosen.length === 0) add(qf + ".answer", "Q" + q.n + " must select at least one option.");
                    if (chosen.some((c) => !keys.includes(c))) add(qf + ".answer", "Q" + q.n + " selects an option that doesn't exist.");
                    if (g.chooseCount && chosen.length !== Number(g.chooseCount)) add(qf + ".answer", "Q" + q.n + " must select exactly " + g.chooseCount + " options.");
                } else if (fam === "match") {
                    const keys = (g.options || []).map((o) => o.key);
                    if (!key.answer || !keys.includes(key.answer)) add(qf + ".answer", "Q" + q.n + " must match one of the " + (def.optionsLabel || "options") + ".");
                }

                if (key.evidenceId && !evidenceIds.has(key.evidenceId)) add(qf + ".evidenceId", "Q" + q.n + " references missing evidence '" + key.evidenceId + "'.");
            });
        });
    } else {
        // Draft: flag dangling evidence as a non-blocking warning.
        (session.answerKey || []).forEach((key) => {
            if (key.evidenceId && !evidenceIds.has(key.evidenceId)) warnings.push({ field: "answerKey[" + key.n + "].evidenceId", message: "References missing evidence '" + key.evidenceId + "'." });
        });
    }

    // Warning: evidence spans referenced by no question.
    const referenced = new Set((session.answerKey || []).map((a) => a.evidenceId).filter(Boolean));
    evidenceIds.forEach((id) => { if (!referenced.has(id)) warnings.push({ field: "passageHtml", message: "Evidence '" + id + "' is not used by any question." }); });

    return { errors, warnings, ok: errors.length === 0 };
}
