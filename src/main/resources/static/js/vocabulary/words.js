/* =========================================================================
   vocabulary/words.js — /vocabulary, My words (vocabulary requirement 4):
   totals, the Learn and Sets cards, adding a word or phrase, tabs, search and
   the list. Words the AI is still preparing are polled every 3 s (2 min).
   ========================================================================= */

import { ApiError, currentUser } from "../auth.js";
import * as api from "./api.js";
import { dots, el } from "./api.js";

const $ = (id) => document.getElementById(id);
const SIZE = 50;
const state = { tab: "all", q: "", page: 0, words: [], total: 0, today: null, polling: null, pollUntil: 0 };

function plural(n, word) {
    return n + " " + word + (n === 1 ? "" : "s");
}

async function load(append = false) {
    const page = await api.words({ state: state.tab, q: state.q, page: state.page, size: SIZE });
    state.words = append ? state.words.concat(page.words) : page.words;
    state.total = page.total;
    state.today = page.today;
    summary(page);
    list();
    poll();
}

function summary(page) {
    $("vAll").textContent = page.counts.all;
    $("vNew").textContent = page.counts.fresh;
    $("vLearning").textContent = page.counts.learning;
    $("vMastered").textContent = page.counts.mastered;
    const ready = Math.min(5, page.newReady);
    $("vLearnTitle").textContent = ready ? plural(ready, "new word") + " ready" : "No new words";
    $("vLearnText").textContent = ready
        ? "Meet each word, practise it, then prove it — a word you miss comes back until you get it."
        : page.counts.fresh ? "Your new words are being prepared…" : "Save words from a passage, or add one below.";
    $("vLearnGo").hidden = !ready;
    setsCard(page.sets);
    document.querySelectorAll("#vTabs button").forEach((b) => {
        const key = b.dataset.state;
        const n = { all: page.counts.all, new: page.counts.fresh, learning: page.counts.learning, mastered: page.counts.mastered }[key];
        b.textContent = b.textContent.split(" · ")[0] + " · " + n;
    });
}

/** "Set 2 of 3 · 18 / 25 mastered", the set's dots, and Continue (requirement 4.1). */
function setsCard(sets) {
    const all = sets.sets;
    const open = all.find((s) => s.number === sets.current);
    const go = $("vSetsGo");
    if (!all.length) {
        $("vSetsTitle").textContent = "No sets yet";
        $("vSetsDots").replaceChildren();
        $("vSetsText").textContent = "Learn some words first — they join Set 1.";
        go.hidden = true;
        return;
    }
    const set = open || all[all.length - 1];
    $("vSetsTitle").textContent = "Set " + set.number + " of " + all.length + " · " + set.mastered + " / " + set.size + " mastered";
    $("vSetsDots").replaceChildren(dots(set.streaks));
    const left = set.size - set.mastered;
    $("vSetsText").textContent = open
        ? "See the Azerbaijani, type the English. " + plural(left, "word") + " left in this set."
        : "Every set is mastered. New learned words start the next set.";
    go.textContent = open ? "Continue Set " + set.number + " →" : "Open sets";
    go.hidden = false;
}

function list() {
    const box = $("vList");
    box.replaceChildren(...state.words.map(row));
    $("vEmpty").hidden = state.words.length > 0;
    $("vEmpty").textContent = state.q ? "No words match “" + state.q + "”." : "No words here yet.";
    $("vMore").hidden = state.words.length >= state.total;
}

function row(w) {
    const card = el("div", "v-card v-word");
    card.dataset.id = w.id;
    card.dataset.word = w.word;
    const word = el("div", "v-word__word", w.word);
    const tags = [w.pos, w.level].filter(Boolean).join(" · ");
    if (tags) word.append(el("small", null, tags));
    const mean = el("div", "v-word__mean");
    if (w.meaning) {
        mean.append(document.createTextNode(w.meaning.en));
        if (w.meaning.az) mean.append(el("span", "v-word__az", " · " + w.meaning.az));
    } else {
        mean.append(el("span", "v-word__az", w.cardStatus === "FAILED" ? "We couldn't prepare this word." : "Preparing the meaning…"));
    }
    const from = w.source && w.source.title ? "from: " + w.source.title : "added by you";
    mean.append(el("small", null, from + " · added " + day(w.addedOn)));
    card.append(word, mean, status(w));
    const remove = el("button", "v-remove", "✕");
    remove.type = "button";
    remove.title = "Remove";
    remove.setAttribute("aria-label", "Remove “" + w.word + "”");
    remove.addEventListener("click", async () => {
        remove.disabled = true;
        try {
            await api.remove(w.id);
            api.changed();
            await load();
        } catch (e) {
            remove.disabled = false;
        }
    });
    card.append(remove);
    return card;
}

function status(w) {
    const box = el("div", "v-word__status v-strength");
    if (w.cardStatus === "PREPARING") {
        box.append(el("span", "v-state v-state--preparing", "Preparing… ⏳"));
    } else if (w.cardStatus === "FAILED") {
        box.append(el("span", "v-state v-state--failed", "Couldn't prepare"));
        const again = el("button", "btn btn--text", "Try again");
        again.type = "button";
        again.addEventListener("click", async () => {
            again.disabled = true;
            try {
                await api.retry(w.id);
                await load();
            } catch (e) {
                message(e instanceof ApiError ? e.message : "That didn't work.", true);
                again.disabled = false;
            }
        });
        box.append(again);
    } else if (w.state === "NEW") {
        box.append(el("span", "v-state v-state--new", "New"));
    } else if (w.state === "MASTERED") {
        box.append(el("span", "v-state v-state--mastered", "✓ Mastered"));
    } else {
        const once = w.streak === 1;
        const dot = dots([w.streak]);
        dot.classList.add("v-dots--inline");
        box.append(dot, el("em", null, (w.set ? "Set " + w.set + " · " : "") + (once ? "right once" : "not yet")));
    }
    return box;
}

const utc = (iso) => {
    const [y, m, d] = iso.split("-").map(Number);
    return Date.UTC(y, m - 1, d);
};

/** "today", "yesterday" or the date, counted from the server's today (the browser's clock may be on another day). */
function day(iso) {
    if (!iso) return "";
    const [y, m, d] = iso.split("-").map(Number);
    const date = new Date(y, m - 1, d);
    const diff = state.today ? Math.round((utc(state.today) - utc(iso)) / 86400000) : null;
    if (diff === 0) return "today";
    if (diff === 1) return "yesterday";
    return date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

/** Words the AI is preparing: ask again every 3 s, for up to 2 minutes. */
function poll() {
    clearTimeout(state.polling);
    const preparing = state.words.filter((w) => w.cardStatus === "PREPARING").map((w) => w.id);
    if (!preparing.length) return;
    if (!state.pollUntil) state.pollUntil = Date.now() + 120000;
    if (Date.now() > state.pollUntil) return;
    state.polling = setTimeout(async () => {
        try {
            const fresh = await api.wordsByIds(preparing);
            const done = fresh.filter((w) => w.cardStatus !== "PREPARING");
            const gone = preparing.filter((id) => !fresh.some((w) => w.id === id));
            if (done.length || gone.length) {
                state.pollUntil = 0;
                await load();
                if (gone.length) message("A word was removed: it isn't an English word we can teach.", true);
                return;
            }
        } catch (e) {
            /* try again next time */
        }
        poll();
    }, 3000);
}

function message(text, bad) {
    const m = $("vAddMsg");
    m.textContent = text;
    m.className = "v-msg" + (bad ? " is-bad" : " is-ok");
    m.hidden = !text;
}

async function add(e) {
    e.preventDefault();
    const input = $("vAddInput");
    const text = input.value.trim();
    if (!text) return input.focus();
    $("vAddBtn").disabled = true;
    try {
        const result = await api.save(text, null);
        input.value = "";
        message(result.alreadySaved ? "“" + result.word.word + "” is already in your words."
            : result.word.cardStatus === "PREPARING" ? "＋ “" + result.word.word + "” added — preparing its meaning…"
                : "＋ “" + result.word.word + "” added.", false);
        state.tab = "all";
        state.page = 0;
        tabs();
        state.pollUntil = 0;
        api.changed();
        await load();
    } catch (err) {
        const detail = err instanceof ApiError ? (err.errors[0] ? err.errors[0].message : err.message) : "That didn't work.";
        message(detail, true);
    } finally {
        $("vAddBtn").disabled = false;
    }
}

function tabs() {
    document.querySelectorAll("#vTabs button").forEach((b) => {
        b.classList.toggle("is-on", b.dataset.state === state.tab);
        b.setAttribute("aria-pressed", String(b.dataset.state === state.tab));
    });
}

async function start() {
    const user = await currentUser();
    $("vStatus").hidden = true;
    if (!user) {
        $("vGuest").hidden = false;
        return;
    }
    $("vPage").hidden = false;
    $("vAdd").addEventListener("submit", add);
    $("vTabs").addEventListener("click", (e) => {
        const b = e.target.closest("button[data-state]");
        if (!b) return;
        state.tab = b.dataset.state;
        state.page = 0;
        tabs();
        load();
    });
    let typing = null;
    $("vSearch").addEventListener("input", (e) => {
        clearTimeout(typing);
        typing = setTimeout(() => {
            state.q = e.target.value.trim();
            state.page = 0;
            load();
        }, 250);
    });
    $("vMore").addEventListener("click", () => {
        state.page += 1;
        load(true);
    });
    try {
        await load();
    } catch (e) {
        $("vPage").hidden = true;
        $("vStatus").hidden = false;
        $("vStatus").textContent = "We couldn't load your words. Check your connection and reload the page.";
    }
}

start();
