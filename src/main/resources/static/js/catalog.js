/* =========================================================================
   catalog.js — the reading catalog (/reading) on GET /api/v1/exercises.
   The filters live in the page URL (?level=B1&type=TFNG&page=1…), so a
   filtered list can be bookmarked and the back button works. Signed-in
   learners also see what they've done and can hide it.
   ========================================================================= */

import { apiFetch, currentUser } from "./auth.js";
import { coverImage } from "./covers.js";

export const TYPE_NAMES = {
    TFNG: "True / False / Not Given", YNNG: "Yes / No / Not Given", MCQ: "Multiple choice", MCMA: "Choose several",
    FILL: "Sentence completion", SUMMARY: "Summary completion", SHORT: "Short answer", DIAGRAM_LABEL: "Diagram",
    MATCH_HEADINGS: "Matching headings", MATCH_INFO: "Matching information", MATCH_FEATURES: "Matching features",
    MATCH_ENDINGS: "Sentence endings", SUMMARY_BOX: "Summary from a box",
};
const PAGE_SIZE = 12;
const LANGUAGE_KEY = "bandup.helpLanguage";

const $ = (id) => document.getElementById(id);
const els = {
    items: $("catalogItems"), count: $("catalogCount"), status: $("catalogStatus"), pager: $("catalogPager"),
    pageInfo: $("pageInfo"), prev: $("prevPage"), next: $("nextPage"), format: $("formatFilter"), time: $("timeFilter"),
    notDone: $("notDoneFilter"), notDoneLabel: $("notDoneLabel"), clear: $("clearFilters"),
};
let signedIn = false;

function helpLanguage() {
    try {
        return localStorage.getItem(LANGUAGE_KEY);
    } catch (e) {
        return null;
    }
}

/** The filters as the URL has them. */
function readState() {
    const p = new URLSearchParams(location.search);
    return {
        level: p.getAll("level"), type: p.getAll("type"), topic: p.getAll("topic"),
        format: p.get("format") || "", maxMinutes: p.get("maxMinutes") || "",
        notDone: p.get("notDone") === "true", page: Math.max(0, Number(p.get("page")) || 0),
    };
}

function toParams(state, forApi) {
    const p = new URLSearchParams();
    state.level.forEach((v) => p.append("level", v));
    state.type.forEach((v) => p.append("type", v));
    state.topic.forEach((v) => p.append("topic", v));
    if (state.format) p.set("format", state.format);
    if (state.maxMinutes) p.set("maxMinutes", state.maxMinutes);
    if (state.notDone) p.set("notDone", "true");
    if (state.page) p.set("page", String(state.page));
    if (forApi) {
        p.set("skill", "READING");
        p.set("size", String(PAGE_SIZE));
        const lang = helpLanguage();
        if (lang) p.set("lang", lang);
    }
    return p;
}

/** The filters as the form shows them. */
function formState() {
    const checked = (name) => [...document.querySelectorAll('[data-chips="' + name + '"] input:checked')].map((i) => i.value);
    return {
        level: checked("level"), type: checked("type"), topic: checked("topic"),
        format: els.format.value, maxMinutes: els.time.value, notDone: signedIn && els.notDone.checked, page: 0,
    };
}

function chip(group, value, label, checked) {
    const wrap = document.createElement("label");
    wrap.className = "chip";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.name = group;
    input.value = value;
    input.checked = checked;
    const text = document.createElement("span");
    text.textContent = label;
    wrap.append(input, text);
    return wrap;
}

function renderFilters(filters, state) {
    const fill = (name, values, label) => {
        const box = document.querySelector('[data-chips="' + name + '"]');
        box.textContent = "";
        values.forEach((v) => box.appendChild(chip(name, label(v).value, label(v).text, state[name].includes(label(v).value))));
        box.closest(".filter").hidden = values.length === 0;
    };
    fill("level", filters.levels, (l) => ({ value: l, text: l }));
    fill("type", filters.types, (t) => ({ value: t, text: TYPE_NAMES[t] || t }));
    fill("topic", filters.topics, (t) => ({ value: t.slug, text: t.name }));
    els.format.value = state.format;
    els.time.value = state.maxMinutes;
    els.notDone.checked = state.notDone;
}

export function card(item, signedIn) {
    const a = document.createElement("a");
    a.className = "ex-card";
    a.href = "/reading/" + encodeURIComponent(item.id);
    a.dataset.id = item.id;

    const cover = document.createElement("div");
    cover.className = "ex-cover";
    cover.appendChild(coverImage(item.coverUrl, item.topics));
    if (signedIn && item.done) {
        const done = document.createElement("span");
        done.className = "ex-done";
        done.textContent = "✓ Done";
        cover.appendChild(done);
    }

    const body = document.createElement("div");
    body.className = "ex-body";
    const title = document.createElement("h2");
    title.textContent = item.title;
    const meta = document.createElement("div");
    meta.className = "ex-meta";
    const add = (text, cls) => {
        const s = document.createElement("span");
        s.textContent = text;
        if (cls) s.className = cls;
        meta.appendChild(s);
    };
    if (item.level) add(item.level, "ex-meta__level");
    add(item.format === "FULL" ? "Full test" : "Mini");
    add(item.durationSeconds ? Math.round(item.durationSeconds / 60) + " min" : "No timer");
    add(item.questionCount + (item.questionCount === 1 ? " question" : " questions"));
    body.append(title, meta);
    if (item.topics.length) {
        const topics = document.createElement("div");
        topics.className = "ex-topics";
        topics.textContent = item.topics.map((t) => t.name).join(", ");
        body.appendChild(topics);
    }
    if (item.types.length) {
        const types = document.createElement("div");
        types.className = "ex-topics";
        types.textContent = item.types.map((t) => TYPE_NAMES[t] || t).join(" · ");
        body.appendChild(types);
    }
    if (signedIn) {
        const progress = document.createElement("div");
        progress.className = "ex-progress" + (item.done ? "" : " ex-progress--new");
        progress.textContent = item.done && item.bestScore
            ? "Best score " + item.bestScore.score + "/" + item.bestScore.total
            : "Not done yet";
        body.appendChild(progress);
    }
    a.append(cover, body);
    return a;
}

function showStatus(text) {
    els.status.textContent = text;
    els.status.hidden = !text;
}

async function load(state) {
    showStatus("Loading…");
    els.items.textContent = "";
    try {
        const page = await apiFetch("/api/v1/exercises?" + toParams(state, true));
        const filtered = state.level.length || state.type.length || state.topic.length || state.format || state.maxMinutes || state.notDone;
        showStatus(page.items.length ? "" : filtered ? "No exercises match these filters." : "No exercises yet.");
        page.items.forEach((item) => els.items.appendChild(card(item, signedIn)));
        els.count.textContent = page.totalItems === 1 ? "1 exercise" : page.totalItems + " exercises";
        const pages = Math.max(1, Math.ceil(page.totalItems / page.size));
        els.pager.hidden = pages <= 1;
        els.pageInfo.textContent = "Page " + (page.page + 1) + " of " + pages;
        els.prev.disabled = page.page === 0;
        els.next.disabled = page.page + 1 >= pages;
    } catch (e) {
        showStatus("Couldn't load the exercises. Please try again.");
        els.count.textContent = "";
    }
}

function go(state) {
    const query = toParams(state, false).toString();
    history.replaceState(null, "", "/reading" + (query ? "?" + query : ""));
    return load(state);
}

async function start() {
    const user = await currentUser();
    signedIn = !!user;
    els.notDoneLabel.hidden = !signedIn;
    const state = readState();
    try {
        const lang = helpLanguage();
        renderFilters(await apiFetch("/api/v1/exercises/filters" + (lang ? "?lang=" + encodeURIComponent(lang) : "")), state);
    } catch (e) {
        /* the list still works without filter choices */
    }
    document.getElementById("catalogFilters").addEventListener("change", () => go(formState()));
    els.clear.addEventListener("click", () => {
        document.querySelectorAll("#catalogFilters input[type=checkbox]").forEach((i) => (i.checked = false));
        els.format.value = "";
        els.time.value = "";
        go(formState());
    });
    els.prev.addEventListener("click", () => { const s = readState(); s.page = Math.max(0, s.page - 1); go(s); });
    els.next.addEventListener("click", () => { const s = readState(); s.page += 1; go(s); });
    await load(state);
}

if (document.getElementById("catalogRoot")) start();
