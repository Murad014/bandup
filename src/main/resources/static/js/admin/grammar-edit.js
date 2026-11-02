/* =========================================================================
   grammar-edit.js — /admin/grammar/new and /admin/grammar/{slug}/edit
   (grammar requirement 12, design §6.5). The lesson is edited as the JSON
   of lesson-format.md (what n8n sends) with a live preview that shows the
   answers. The server checks every format rule on save and lists all
   problems with their JSON paths. Archived lessons are read-only.
   ========================================================================= */

import { grammarApi as api } from "./grammar-api.js";
import { adminReady, setUnsavedCheck } from "./guard.js";
import { el, rich } from "../grammar/items.js";

await adminReady;

const $ = (id) => document.getElementById(id);
const json = $("gJson");
const STATE_FIELDS = ["version", "status", "revision", "publishedAt"];

const SKELETON = {
    id: "b1-topic-name-1",
    level: "B1",
    topic: { key: "topic-name", label: "Topic name" },
    title: "",
    goal: "By the end, you'll know …",
    minutes: 20,
    position: null,
    learn: [
        { icon: "🌉", label: "The idea", heading: "", body: "", examples: [{ ok: true, text: "" }] },
        { icon: "🧭", label: "How to decide", heading: "", steps: ["1️⃣ …", "2️⃣ …"], tip: "" },
        { icon: "⚠️", label: "Common mistake", heading: "", examples: [{ ok: false, text: "" }, { ok: true, text: "" }] },
    ],
    worked: { question: "", steps: ["🔍 …", "✅ …"], answer: "" },
    practice: [
        { id: "p1", type: "choose", prompt: "___", options: ["", ""], answer: 0, whys: ["", ""], explanation: "" },
        { id: "p2", type: "type", prompt: "", sentence: "___", accept: [""], explanation: "" },
    ],
    check: [
        { id: "c1", type: "choose", prompt: "___", options: ["", ""], answer: 0, explanation: "" },
        { id: "c2", type: "type", prompt: "", sentence: "___", accept: [""], explanation: "" },
        { id: "c3", type: "fix", prompt: "This sentence is wrong. Type the correct form:", wrong: "", accept: [""], explanation: "" },
        { id: "c4", type: "build", prompt: "Build the sentence:", tiles: ["", ""], answer: "", explanation: "" },
        { id: "c5", type: "choose", prompt: "___", options: ["", ""], answer: 0, explanation: "" },
    ],
};

let timer = null;
const state = { slug: $("gEditRoot").dataset.lessonId || null, version: null, status: "DRAFT", revision: 1, dirty: false, busy: false };

// ------------------------------------------------------------------ state

function setDirty(value) {
    state.dirty = value;
    $("gDirty").hidden = !value;
}

setUnsavedCheck(() => state.dirty);
window.BandUpAdminNav?.registerGuard(() => state.dirty);

function showState() {
    const status = state.status.toLowerCase();
    $("gStatus").className = "badge badge--" + status;
    $("gStatus").textContent = status;
    $("gRevision").textContent = state.slug && state.revision > 1 ? "revision " + state.revision : "";
    $("gEditTitle").textContent = state.slug ? "Grammar lesson: " + state.slug : "New grammar lesson";
    const archived = state.status === "ARCHIVED";
    const published = state.status === "PUBLISHED";
    json.readOnly = archived;
    $("gSave").hidden = archived;
    $("gSave").textContent = published ? "Save" : "Save draft";
    $("gPublish").hidden = archived || published;
    $("gPositionBox").hidden = archived || published;
    $("gUnpublish").hidden = !published;
    if (archived) notice("Archived: learners used this lesson, so it is kept read-only.");
}

/** The editable lesson: the admin view without its state fields. */
function editable(view) {
    const copy = { ...view };
    STATE_FIELDS.forEach((f) => delete copy[f]);
    return copy;
}

function adopt(view) {
    state.slug = view.id;
    state.version = view.version;
    state.status = view.status;
    state.revision = view.revision;
    json.value = JSON.stringify(editable(view), null, 2);
    setDirty(false);
    showState();
    preview();
}

// --------------------------------------------------------------- messages

function notice(text) {
    $("gNotice").textContent = text;
    $("gNotice").hidden = !text;
}

function errors(title, list) {
    const box = $("gErrors");
    if (!title) {
        box.hidden = true;
        box.replaceChildren();
        return;
    }
    box.replaceChildren(el("b", null, title));
    if (list && list.length) {
        const ul = el("ul");
        ul.id = "gErrorList";
        for (const e of list) {
            const li = el("li");
            if (e.field) li.append(el("code", null, e.field), " — ");
            li.append(e.message);
            ul.append(li);
        }
        box.append(ul);
    }
    box.hidden = false;
    box.scrollIntoView({ block: "nearest" });
}

function apiFailed(e) {
    if (e && e.status === 422 && e.errors.length) {
        errors("The lesson has " + e.errors.length + (e.errors.length === 1 ? " problem:" : " problems:"), e.errors);
    } else {
        errors(e && e.message ? e.message : "That didn't work. Check your connection and try again.");
    }
}

// ------------------------------------------------------------------- JSON

function parse() {
    try {
        const value = JSON.parse(json.value);
        if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("the lesson must be one JSON object { … }");
        $("gJsonState").textContent = "✓ Valid JSON";
        $("gJsonState").className = "gp-jsonstate is-ok";
        return value;
    } catch (e) {
        $("gJsonState").textContent = "JSON error: " + e.message;
        $("gJsonState").className = "gp-jsonstate is-bad";
        return null;
    }
}

// ---------------------------------------------------------------- preview

/** A text value: a string (English) or {en, az, …}. */
function text(value, className) {
    const box = el("span", className);
    if (value && typeof value === "object") {
        box.append(rich(String(value.en ?? "")));
        for (const [lang, v] of Object.entries(value)) {
            if (lang !== "en") box.append(el("small", "gp-lang", lang + ": " + v));
        }
    } else {
        box.append(rich(value == null ? "" : String(value)));
    }
    return box;
}

const list = (v) => (Array.isArray(v) ? v : []);

function preview() {
    const lesson = parse();
    if (!lesson) return;
    const out = [];
    const head = el("div", "gp-block");
    head.append(el("div", "gp-kick", [lesson.level, lesson.topic && textOf(lesson.topic.label), (lesson.minutes ?? 20) + " min"]
        .filter(Boolean).join(" · ")));
    head.append(text(lesson.title, "gp-title"), text(lesson.goal, "gp-goal"));
    out.push(head);

    list(lesson.learn).forEach((card, i) => {
        const box = el("div", "gp-block gp-card");
        const label = el("div", "gp-kick");
        label.append((card.icon || "") + " ", text(card.label), " · " + (i + 1) + "/" + list(lesson.learn).length);
        box.append(label, text(card.heading, "gp-heading"));
        if (card.body) box.append(text(card.body, "gp-body"));
        list(card.steps).forEach((s) => box.append(text(s, "gp-step")));
        list(card.examples).forEach((ex) => {
            const line = el("div", "gp-ex " + (ex.ok ? "is-ok" : "is-bad"));
            line.append((ex.ok ? "✓ " : "✗ "), text(ex.text));
            box.append(line);
        });
        if (card.tip) {
            const tip = el("div", "gp-tip");
            tip.append("💡 ", text(card.tip));
            box.append(tip);
        }
        out.push(box);
    });

    if (lesson.worked) {
        const w = lesson.worked;
        const box = el("div", "gp-block gp-card");
        box.append(el("div", "gp-kick", "👀 Worked example"), text(w.question, "gp-heading"));
        list(w.steps).forEach((s) => box.append(text(s, "gp-step")));
        const answer = el("div", "gp-answer", "✅ Answer: ");
        answer.append(el("b", null, w.answer ?? ""));
        box.append(answer);
        out.push(box);
    }

    for (const [name, items] of [["✏️ Practice", lesson.practice], ["🎯 Check (must master)", lesson.check]]) {
        out.push(el("h4", "gp-section", name + " · " + list(items).length));
        list(items).forEach((item) => out.push(itemPreview(item)));
    }
    $("gPreview").replaceChildren(...out);
}

function textOf(value) {
    return value && typeof value === "object" ? String(value.en ?? "") : String(value ?? "");
}

function itemPreview(item) {
    const box = el("div", "gp-block gp-item");
    box.dataset.item = item.id || "";
    box.append(el("div", "gp-kick", (item.id || "?") + " · " + (item.type || "?")), text(item.prompt, "gp-prompt"));
    if (item.type === "choose") {
        const ul = el("ul", "gp-options");
        list(item.options).forEach((option, i) => {
            const li = el("li", i === item.answer ? "is-right" : null);
            li.append(el("b", null, (i === item.answer ? "✓ " : "") + option));
            const why = list(item.whys)[i];
            if (why) li.append(" — ", text(why));
            ul.append(li);
        });
        box.append(ul);
    } else if (item.type === "type") {
        box.append(text(item.sentence, "gp-sentence"));
        box.append(accepted(item));
    } else if (item.type === "fix") {
        box.append(el("div", "gp-ex is-bad", "✗ " + (item.wrong ?? "")), accepted(item));
    } else if (item.type === "build") {
        box.append(el("div", "gp-tiles", "Tiles: " + list(item.tiles).join(" · ")));
        const answer = el("div", "gp-answer", "✓ Answer: ");
        answer.append(el("b", null, item.answer ?? ""));
        box.append(answer);
    }
    const explain = el("div", "gp-explain", "Explanation: ");
    explain.append(text(item.explanation));
    box.append(explain);
    return box;
}

function accepted(item) {
    const line = el("div", "gp-answer", "✓ Accepted: ");
    line.append(el("b", null, list(item.accept).join(" | ")));
    if (item.show) line.append(" (shown: " + item.show + ")");
    return line;
}

// ---------------------------------------------------------------- actions

async function save() {
    errors(null);
    notice("");
    clearTimeout(timer);
    preview();
    const lesson = parse();
    if (!lesson) {
        errors("Fix the JSON first: " + $("gJsonState").textContent.replace(/^JSON error: /, ""));
        return false;
    }
    if (state.slug && lesson.id !== state.slug) {
        errors("The id can't change: this lesson is “" + state.slug + "”. To use another id, create a new lesson.");
        return false;
    }
    try {
        const view = state.slug ? await api.update(state.slug, { ...lesson, version: state.version })
            : await api.create(lesson);
        const created = !state.slug;
        adopt(view);
        if (created) history.replaceState(null, "", "/admin/grammar/" + encodeURIComponent(view.id) + "/edit");
        notice(created ? "Saved as a draft." : "Saved.");
        return true;
    } catch (e) {
        apiFailed(e);
        return false;
    }
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

$("gSave").addEventListener("click", () => run($("gSave"), save));

$("gPublish").addEventListener("click", () => run($("gPublish"), async () => {
    errors(null);
    notice("");
    const raw = $("gPosition").value.trim();
    const position = raw ? Number(raw) : null;
    if (position !== null && !(Number.isInteger(position) && position >= 1)) {
        errors("The position must be a whole number from 1 (or empty for the end of the course).");
        return;
    }
    if ((state.dirty || !state.slug) && !(await save())) return;
    try {
        const view = await api.publish(state.slug, position);
        adopt(view);
        notice("Published at position " + view.position + " of the " + view.level + " course.");
    } catch (e) {
        apiFailed(e);
    }
}));

$("gUnpublish").addEventListener("click", () => run($("gUnpublish"), async () => {
    errors(null);
    if (state.dirty && !window.confirm("Unpublish and discard your unsaved changes?")) return;
    try {
        adopt(await api.unpublish(state.slug));
        notice("Unpublished: learners no longer see it.");
    } catch (e) {
        apiFailed(e);
    }
}));

json.addEventListener("input", () => {
    setDirty(true);
    clearTimeout(timer);
    timer = setTimeout(preview, 250);
});

async function start() {
    if (!state.slug) {
        json.value = JSON.stringify(SKELETON, null, 2);
        showState();
        preview();
        return;
    }
    try {
        adopt(await api.get(state.slug));
    } catch (e) {
        json.readOnly = true;
        ["gSave", "gPublish", "gUnpublish", "gPositionBox"].forEach((id) => ($(id).hidden = true));
        errors(e && e.status === 404 ? "No such lesson: “" + state.slug + "”." : "Couldn't load the lesson. Reload the page to try again.");
    }
}

start();
