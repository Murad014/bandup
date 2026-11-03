/* =========================================================================
   vocabulary/save.js — saving words on the reading page (vocabulary
   requirement 1). Listens to the reading page's events:
     bandup:gloss      a dotted word's popover opened → "＋ Add to my words"
     bandup:selection  text selected in the passage   → the same next to Highlight
     bandup:passage    the passage is on screen       → mark saved words (teal),
                                                         "New words from this passage"
   Guests are asked to sign in. The word's sentence goes with it as context.
   ========================================================================= */

import { ApiError, currentUser } from "../auth.js";
import * as api from "./api.js";
import { el } from "./api.js";

const MAX_WORDS = 3;
const MAX_LENGTH = 40;
const state = { user: undefined, exercise: null, passage: null, saved: [], selection: null };

const normal = (text) => String(text || "").replace(/[’‘]/g, "'").trim().replace(/\s+/g, " ")
    .replace(/^[^a-z0-9']+|[^a-z0-9']+$/gi, "").toLowerCase();

async function user() {
    if (state.user === undefined) state.user = await currentUser();
    return state.user;
}

/** The forms of every word the learner saved from this passage. */
function savedForms() {
    const forms = new Set();
    state.saved.forEach((w) => (w.forms || [w.word]).forEach((f) => forms.add(f)));
    return forms;
}

function savedWordFor(text) {
    const form = normal(text);
    return state.saved.find((w) => (w.forms || [w.word]).includes(form));
}

/** The sentence around a node of the passage (context for the AI and the learner). */
function sentenceAround(node, word) {
    const block = node && (node.closest ? node : node.parentElement);
    const container = block ? block.closest("p, li, .ev, #passage") || block : null;
    const text = container ? container.textContent.replace(/\s+/g, " ").trim() : "";
    const sentences = text.split(/(?<=[.!?])\s+(?=[A-Z“"(])/);
    const lower = word.toLowerCase();
    const found = sentences.find((s) => s.toLowerCase().includes(lower)) || "";
    return found.length > 400 ? found.slice(0, 400) : found || null;
}

async function save(text, node) {
    const sentence = sentenceAround(node, text);
    const result = await api.save(text, state.exercise ? { exercise: state.exercise, sentence } : null);
    api.changed();
    await refresh();
    return result;
}

function failure(e) {
    if (e instanceof ApiError) return e.errors && e.errors[0] ? e.errors[0].message : e.message;
    return "That didn't work. Try again.";
}

// ---------------------------------------------------------------- popover

async function onGloss(event) {
    const { pop, word } = event.detail;
    const text = word.textContent;
    const row = el("div", "pop-save");
    pop.append(row);
    const me = await user();
    if (!me) {
        const signIn = el("a", null, "Sign in to save words");
        signIn.href = "/login?next=" + encodeURIComponent(location.pathname);
        row.append(signIn);
        return;
    }
    paintPopRow(row, text, word);
}

function paintPopRow(row, text, word) {
    const saved = savedWordFor(text);
    row.replaceChildren();
    if (saved) {
        row.append(el("span", "pop-saved", "✓ In my words"));
        const remove = el("button", "pop-remove", "Remove");
        remove.type = "button";
        remove.addEventListener("click", async (e) => {
            e.stopPropagation();
            remove.disabled = true;
            try {
                await api.remove(saved.id);
                api.changed();
                await refresh();
                paintPopRow(row, text, word);
            } catch (err) {
                row.append(el("small", null, failure(err)));
            }
        });
        row.append(remove);
        return;
    }
    const add = el("button", null, "＋ Add to my words");
    add.type = "button";
    add.dataset.saveWord = "";
    add.addEventListener("click", async (e) => {
        e.stopPropagation();
        add.disabled = true;
        try {
            const result = await save(text, word);
            paintPopRow(row, text, word);
            if (result.word.cardStatus === "PREPARING") row.append(el("small", null, "Preparing its meaning…"));
        } catch (err) {
            add.disabled = false;
            row.append(el("small", null, failure(err)));
        }
    });
    row.append(add);
}

// -------------------------------------------------------------- selection

function saveButton() {
    return document.getElementById("saveWordBtn");
}

function onSelection(event) {
    const button = saveButton();
    if (!button) return;
    const { range, button: hl, inPassage } = event.detail;
    const text = range.toString().trim();
    const words = text.split(/\s+/).filter(Boolean);
    if (!inPassage || !text || words.length > MAX_WORDS || text.length > MAX_LENGTH || !/[a-z]/i.test(text)) {
        button.hidden = true;
        return;
    }
    state.selection = { range, text };
    button.textContent = savedWordFor(text) ? "✓ In my words" : "＋ Add to my words";
    button.disabled = !!savedWordFor(text);
    button.hidden = false;
    const r = hl.getBoundingClientRect();
    button.style.top = (r.top + (window.scrollY || 0)) + "px";
    const left = r.right + (window.scrollX || 0) + 6;
    const max = document.documentElement.clientWidth - (button.offsetWidth || 160) - 8;
    if (left > max) {
        button.style.left = Math.max(8, r.left + (window.scrollX || 0)) + "px";
        button.style.top = (r.bottom + (window.scrollY || 0) + 6) + "px";
    } else {
        button.style.left = left + "px";
    }
}

function onSelectionEnd() {
    const button = saveButton();
    if (button) button.hidden = true;
}

async function onSaveSelection() {
    const button = saveButton();
    const picked = state.selection;
    if (!picked) return;
    const me = await user();
    if (!me) {
        location.href = "/login?next=" + encodeURIComponent(location.pathname);
        return;
    }
    button.disabled = true;
    try {
        await save(picked.text, picked.range.commonAncestorContainer);
        button.textContent = "✓ Added";
        const sel = window.getSelection && window.getSelection();
        if (sel && sel.removeAllRanges) sel.removeAllRanges();
        setTimeout(() => (button.hidden = true), 900);
    } catch (e) {
        button.textContent = failure(e);
        setTimeout(() => {
            button.hidden = true;
            button.disabled = false;
        }, 2500);
    }
}

// ------------------------------------------------------- passage + marks

async function onPassage(event) {
    state.passage = event.detail.passage;
    state.exercise = event.detail.exercise;
    await refresh();
}

async function refresh() {
    if (!state.passage || !state.exercise) return;
    const me = await user();
    if (!me) {
        box([]);
        return;
    }
    try {
        const page = await api.words({ exercise: state.exercise, size: 100 });
        state.saved = page.words;
    } catch (e) {
        state.saved = [];
    }
    mark();
    box(state.saved.filter((w) => w.state === "NEW"));
}

/** Teal marks on saved words: dotted words, and the same words anywhere in the passage. */
function mark() {
    const forms = savedForms();
    state.passage.querySelectorAll(".w").forEach((w) => w.classList.toggle("is-saved", forms.has(normal(w.textContent))));
    state.passage.querySelectorAll("span.v-saved").forEach((span) => {
        if (!forms.has(normal(span.textContent))) span.replaceWith(...span.childNodes);
    });
    if (!forms.size) return;
    const list = [...forms].sort((a, b) => b.length - a.length).map((f) => f.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
    const re = new RegExp("\\b(" + list.join("|") + ")\\b", "gi");
    const walker = document.createTreeWalker(state.passage, NodeFilter.SHOW_TEXT, {
        acceptNode: (n) => (n.parentElement.closest(".w, .v-saved, mark") ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT),
    });
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach((node) => {
        const text = node.data;
        re.lastIndex = 0;
        if (!re.test(text)) return;
        re.lastIndex = 0;
        const parts = [];
        let last = 0;
        let m;
        while ((m = re.exec(text))) {
            if (m.index > last) parts.push(document.createTextNode(text.slice(last, m.index)));
            const span = el("span", "v-saved", m[0]);
            parts.push(span);
            last = m.index + m[0].length;
        }
        if (last < text.length) parts.push(document.createTextNode(text.slice(last)));
        node.replaceWith(...parts);
    });
}

/** "New words from this passage" with a link to learn just them. */
function box(fresh) {
    const section = document.getElementById("passageWords");
    if (!section) return;
    section.hidden = fresh.length === 0;
    if (!fresh.length) return;
    const chips = el("div", "pwords__chips");
    chips.append(...fresh.map((w) => el("span", null, w.word)));
    const learn = el("a", "btn btn--primary", "Learn these words →");
    learn.href = "/vocabulary/learn?exercise=" + encodeURIComponent(state.exercise);
    section.replaceChildren(el("h3", null, "📚 New words from this passage · " + fresh.length),
        el("p", null, "They wait in Vocabulary. Learn them after the test — 5 at a time."), chips, learn);
}

export function initWordSaving() {
    document.addEventListener("bandup:gloss", onGloss);
    document.addEventListener("bandup:selection", onSelection);
    document.addEventListener("bandup:selection-end", onSelectionEnd);
    document.addEventListener("bandup:passage", onPassage);
    const button = saveButton();
    if (button) {
        button.addEventListener("mousedown", (e) => e.preventDefault()); // keep the selection
        button.addEventListener("click", onSaveSelection);
    }
}
