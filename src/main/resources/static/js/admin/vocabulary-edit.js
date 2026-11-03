/* =========================================================================
   vocabulary-edit.js — /admin/vocabulary/{id}/edit, one word card
   (vocabulary requirement 8; card-format.md). The server checks every rule
   on save and names each problem's field; the field is marked here.
   ========================================================================= */

import { vocabularyApi as api } from "./vocabulary-api.js";
import { adminReady, setUnsavedCheck } from "./guard.js";

await adminReady;

const $ = (id) => document.getElementById(id);
const id = Number($("vcEditRoot").dataset.cardId);
const state = { card: null, dirty: false, busy: false };

setUnsavedCheck(() => state.dirty);
window.BandUpAdminNav?.registerGuard(() => state.dirty);

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

const lines = (text) => text.split("\n").map((s) => s.trim()).filter(Boolean);

function dirty(value) {
    state.dirty = value;
    $("vcDirty").hidden = !value;
}

function fill(card) {
    state.card = card;
    $("vcTitle").textContent = "Word card: " + (card.lemma || card.forms[0] || "");
    const status = String(card.status).toLowerCase();
    $("vcBadge").className = "badge badge--" + status;
    $("vcBadge").textContent = status;
    $("vcInfo").textContent = "revision " + card.revision + " · written by " + (card.source === "ADMIN" ? "an admin" : "the AI")
        + " · saved by " + card.saves + (card.saves === 1 ? " learner" : " learners");
    $("lemma").value = card.lemma || "";
    $("forms").value = (card.forms || []).join(", ");
    $("pos").value = card.pos || "";
    $("level").value = card.level || "B1";
    $("ipa").value = card.ipa || "";
    $("meaningEn").value = card.meaning ? card.meaning.en : "";
    $("meaningAz").value = card.meaning ? card.meaning.az : "";
    $("example0").value = (card.examples || [])[0] || "";
    $("example1").value = (card.examples || [])[1] || "";
    $("collocations").value = (card.collocations || []).join("\n");
    $("family").value = (card.family || []).join("\n");
    $("gapSentence").value = card.gap ? card.gap.sentence : "";
    $("gapAnswer").value = card.gap ? card.gap.answer : "";
    $("pairSentence").value = card.pair ? card.pair.sentence : "";
    const options = card.pair ? card.pair.options : ["", "", "", ""];
    $("pairOptions").replaceChildren(...[0, 1, 2, 3].map((i) => {
        const row = el("label", "vc-option");
        const radio = el("input");
        radio.type = "radio";
        radio.name = "pairAnswer";
        radio.value = String(i);
        radio.checked = card.pair ? card.pair.answer === i : i === 0;
        radio.setAttribute("aria-label", "Option " + (i + 1) + " is right");
        const text = el("input");
        text.type = "text";
        text.id = "pairOption" + i;
        text.value = options[i] || "";
        text.setAttribute("aria-label", "Option " + (i + 1));
        row.append(radio, text);
        return row;
    }));
    [0, 1, 2].forEach((i) => ($("wrong" + i).value = (card.wrongMeanings || [])[i] || ""));
    const preparing = status === "preparing";
    $("vcSave").disabled = preparing;
    $("vcRegenerate").hidden = !(status === "ready" || status === "failed");
    $("vcDelete").hidden = card.saves > 0;
    if (preparing) notice("The AI is writing this card. Reload in a moment.");
    else if (card.lastError && status !== "ready") notice("Last problem: " + card.lastError);
    dirty(false);
}

function read() {
    const answer = document.querySelector("input[name=pairAnswer]:checked");
    return {
        ...state.card,
        lemma: $("lemma").value,
        forms: $("forms").value.split(",").map((s) => s.trim()).filter(Boolean),
        pos: $("pos").value,
        level: $("level").value,
        ipa: $("ipa").value,
        meaning: { en: $("meaningEn").value, az: $("meaningAz").value },
        examples: [$("example0").value, $("example1").value],
        collocations: lines($("collocations").value),
        family: lines($("family").value),
        gap: { sentence: $("gapSentence").value, answer: $("gapAnswer").value },
        pair: { sentence: $("pairSentence").value, options: [0, 1, 2, 3].map((i) => $("pairOption" + i).value),
            answer: answer ? Number(answer.value) : 0 },
        wrongMeanings: [0, 1, 2].map((i) => $("wrong" + i).value),
    };
}

function notice(text) {
    $("vcNotice").textContent = text;
    $("vcNotice").hidden = !text;
}

function errors(title, list) {
    document.querySelectorAll(".field.has-error").forEach((f) => f.classList.remove("has-error"));
    document.querySelectorAll(".field .field-err").forEach((e) => e.remove());
    const box = $("vcErrors");
    if (!title) {
        box.hidden = true;
        return;
    }
    box.replaceChildren(el("b", null, title));
    if (list && list.length) {
        const ul = el("ul");
        ul.id = "vcErrorList";
        list.forEach((e) => {
            const li = el("li");
            if (e.field) li.append(el("code", null, e.field), " — ");
            li.append(e.message);
            ul.append(li);
            const field = document.querySelector('[data-field="' + e.field + '"]')
                || document.querySelector('[data-field="' + e.field.replace(/\[\d+\]$/, "") + '"]');
            if (field) {
                field.classList.add("has-error");
                field.append(el("span", "field-err", e.message));
            }
        });
        box.append(ul);
    }
    box.hidden = false;
}

async function run(button, work) {
    if (state.busy) return;
    state.busy = true;
    button.disabled = true;
    try {
        await work();
    } finally {
        state.busy = false;
        button.disabled = false;
    }
}

$("vcForm").addEventListener("input", () => dirty(true));
$("vcForm").addEventListener("change", () => dirty(true));

$("vcSave").addEventListener("click", () => run($("vcSave"), async () => {
    errors(null);
    notice("");
    try {
        fill(await api.update(id, read()));
        notice("Saved. Every learner sees the new card now.");
    } catch (e) {
        if (e && e.status === 422 && e.errors.length) {
            errors("The card has " + e.errors.length + (e.errors.length === 1 ? " problem:" : " problems:"), e.errors);
        } else {
            errors(e && e.message ? e.message : "That didn't work.");
        }
    }
}));

$("vcRegenerate").addEventListener("click", () => run($("vcRegenerate"), async () => {
    if (state.dirty && !window.confirm("Regenerate and discard your unsaved changes?")) return;
    errors(null);
    try {
        fill(await api.regenerate(id));
        notice("The AI is writing this card again. Learners keep their word meanwhile.");
    } catch (e) {
        errors(e && e.message ? e.message : "That didn't work.");
    }
}));

$("vcDelete").addEventListener("click", () => run($("vcDelete"), async () => {
    if (!window.confirm("Delete this word card?")) return;
    try {
        await api.remove(id);
        dirty(false);
        location.href = "/admin/vocabulary";
    } catch (e) {
        errors(e && e.message ? e.message : "That didn't work.");
    }
}));

try {
    fill(await api.get(id));
} catch (e) {
    $("vcForm").hidden = true;
    ["vcSave", "vcRegenerate", "vcDelete"].forEach((b) => ($(b).hidden = true));
    errors(e && e.status === 404 ? "No such word card." : "Couldn't load the card. Reload the page to try again.");
}
