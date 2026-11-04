/* =========================================================================
   vocabulary/session.js — /vocabulary/learn and /vocabulary/review
   (vocabulary requirements 5, 6). LEARN: Goal → Meet → Practise → Master
   (a wrong task comes back until right) → Done. REVIEW: the due words, each
   answer moving its word ("⏱ Next review: …"). Graded on the server; leaving
   a running session asks first.
   ========================================================================= */

import { ApiError, currentUser } from "../auth.js";
import * as api from "./api.js";
import { el, say } from "./api.js";
import { showTask } from "./tasks.js";

const root = document.getElementById("sessionRoot");
const kind = root.dataset.kind === "review" ? "review" : "learn";
const exercise = new URLSearchParams(location.search).get("exercise");
const $ = (id) => document.getElementById(id);
const body = $("sBody");
const STAGES = kind === "learn" ? [["goal", "Goal"], ["meet", "Meet"], ["practise", "Practise"], ["master", "Master"]]
    : [["review", "🔁 Daily review"]];
/** {@code days} is counted by the server from its today: the browser's clock may be on another day. */
const NEXT = (days) => {
    if (days <= 1) return "tomorrow";
    if (days % 7 === 0) return "in " + (days === 7 ? "1 week" : days / 7 + " weeks");
    return "in " + days + " days";
};

let session = null;
let inProgress = false;
let retries = 0;

function stage(key, fraction) {
    const at = STAGES.findIndex(([k]) => k === key);
    document.querySelectorAll("#sPills .v-pill").forEach((pill, i) => {
        pill.classList.toggle("is-active", i === at && key !== "done");
        pill.classList.toggle("is-done", i < at || key === "done");
        if (i === at && key !== "done") pill.setAttribute("aria-current", "step");
        else pill.removeAttribute("aria-current");
    });
    $("sBar").style.width = Math.round(Math.min(1, Math.max(0, fraction)) * 100) + "%";
}

function guard(value) {
    inProgress = value;
    const nav = window.BandUpNav;
    if (!nav) return;
    if (value) nav.registerGuard(() => inProgress);
    else nav.clearGuard();
}

function link(label, href, primary) {
    const a = el("a", "btn" + (primary ? " btn--primary" : ""), label);
    a.href = href;
    return a;
}

function message(icon, title, text, actions) {
    const card = el("div", "v-card v-msgcard");
    card.append(el("div", "v-goal__icon", icon), el("h1", null, title), el("p", null, text));
    const row = el("div", "v-actions");
    row.append(...actions);
    card.append(row);
    body.replaceChildren(card);
}

function failed(e) {
    guard(false);
    if (e instanceof ApiError && e.status === 409) {
        message("✅", "This session is finished", "Start a new one from My words.", [link("My words", "/vocabulary", true)]);
    } else {
        message("⚠️", "Something went wrong", "We couldn't reach Orbiko. Check your connection and try again.",
            [link("My words", "/vocabulary", true)]);
    }
}

function bar(label, done, total) {
    const box = el("div", "v-mastery");
    const track = el("div", "v-mastery__track");
    const fill = el("i");
    fill.style.width = Math.round((done / Math.max(1, total)) * 100) + "%";
    track.append(fill);
    const count = el("b", null, done + " / " + total);
    count.id = "masteryCount";
    box.append(el("span", null, label), track, count);
    return box;
}

// ------------------------------------------------------------------ learn

function goal() {
    stage("goal", 0.02);
    const card = el("div", "v-card v-goal");
    card.id = "goalCard";
    const n = session.words.length;
    card.append(el("div", "v-goal__icon", "🎯"), el("h1", null, "Learn " + n + " new word" + (n === 1 ? "" : "s")),
        el("p", null, "By the end you'll know what they mean, how to say them and how to use them in a sentence."));
    const chips = el("div", "v-chips");
    chips.append(...session.words.map((w) => el("span", null, w.word)));
    const go = el("button", "btn btn--primary btn--lg", "📖 Start");
    go.type = "button";
    go.id = "startLearning";
    go.addEventListener("click", () => {
        guard(true);
        meet(0);
    });
    card.append(chips, el("p", null, "⏱ About " + Math.max(5, n * 2) + " minutes · meet → practise → master"), go);
    body.replaceChildren(card);
    go.focus();
}

function meet(i) {
    const words = session.words;
    const w = words[i];
    const last = i === words.length - 1;
    stage("meet", 0.05 + 0.3 * (i / words.length));
    say(w.word);
    const card = el("div", "v-card v-meet");
    card.dataset.card = String(i);
    const kick = el("div", "v-q__kick");
    kick.append(el("span", null, "📖 New word · " + (i + 1) + " of " + words.length), el("span", null, w.from ? "from: " + w.from : ""));
    const head = el("div", "v-meet__head");
    const speak = el("button", "v-say", "🔊");
    speak.type = "button";
    speak.setAttribute("aria-label", "Listen to “" + w.word + "”");
    speak.addEventListener("click", () => say(w.word));
    head.append(el("span", "v-meet__word", w.word), speak, el("span", "v-meet__ipa", w.ipa || ""));
    const tags = el("div", "v-meet__tags");
    [w.pos, w.level].filter(Boolean).forEach((t) => tags.append(el("span", "v-chip", t)));
    card.append(kick, head, tags, el("p", "v-meet__mean", w.meaningEn));
    if (w.meaningAz) {
        const az = el("div", "v-meet__az");
        az.append(el("small", null, "AZ"), document.createTextNode(w.meaningAz));
        card.append(az);
    }
    const examples = (w.sentence ? [{ text: w.sentence, from: w.from || "where you saved it" }] : [])
        .concat(w.examples.map((text) => ({ text })));
    if (examples.length) {
        card.append(el("h3", null, w.sentence ? "Where you saw it, and more" : "Examples"));
        examples.forEach((ex) => {
            const line = el("div", "v-ex");
            if (ex.from) line.append(el("small", null, "📄 " + ex.from));
            line.append(...bolded(ex.text, w.word));
            card.append(line);
        });
    }
    if (w.collocations.length) {
        card.append(el("h3", null, "Goes with"));
        const cols = el("div", "v-cols");
        cols.append(...w.collocations.map((c) => el("span", null, c)));
        card.append(cols);
    }
    if (w.family.length) {
        card.append(el("h3", null, "Word family"));
        const fam = el("div", "v-cols");
        fam.append(...w.family.map((f) => el("span", null, f)));
        card.append(fam);
    }
    const nav = el("div", "v-nav");
    if (i > 0) {
        const back = el("button", "btn", "← Back");
        back.type = "button";
        back.addEventListener("click", () => meet(i - 1));
        nav.append(back);
    }
    const next = el("button", "btn btn--primary v-grow", last ? "✅ Got them — practise" : "Next word →");
    next.type = "button";
    next.id = "meetNext";
    next.addEventListener("click", () => (last ? practise() : meet(i + 1)));
    nav.append(next);
    card.append(nav);
    body.replaceChildren(card);
    next.focus();
}

/** The sentence as text nodes, with the word (any form starting with it) in bold. */
function bolded(text, word) {
    const nodes = [];
    const re = new RegExp("\\b(" + word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\w*)", "gi");
    let last = 0;
    let m;
    while ((m = re.exec(text))) {
        if (m.index > last) nodes.push(document.createTextNode(text.slice(last, m.index)));
        nodes.push(el("b", null, m[1]));
        last = m.index + m[1].length;
    }
    if (last < text.length) nodes.push(document.createTextNode(text.slice(last)));
    return nodes;
}

async function practise() {
    const tasks = session.tasks.filter((t) => t.phase === "PRACTISE");
    for (let i = 0; i < tasks.length; i++) {
        stage("practise", 0.4 + 0.2 * (i / tasks.length));
        try {
            await showTask(body, tasks[i], {
                kick: "✏️ Practise · " + (i + 1) + " of " + tasks.length,
                answer: (value) => api.answer(session.sessionId, tasks[i].id, value),
            });
        } catch (e) {
            failed(e);
            return;
        }
    }
    master();
}

async function master() {
    const queue = session.tasks.filter((t) => t.phase === "MASTER");
    const total = queue.length;
    let mastered = 0;
    while (queue.length) {
        const task = queue[0];
        stage(kind === "learn" ? "master" : "review", kind === "learn" ? 0.6 + 0.4 * (mastered / total) : mastered / total);
        let result;
        try {
            result = await showTask(body, task, {
                kick: kind === "learn" ? "🎯 Prove it · " + (mastered + 1) + " of " + total
                    : "🏷 from: " + (task.from || "your words") + " · box " + task.box,
                header: bar(kind === "learn" ? "Mastery" : "Reviewed", mastered, total),
                answer: (value) => api.answer(session.sessionId, task.id, value),
                footer: footer,
            });
        } catch (e) {
            failed(e);
            return;
        }
        queue.shift();
        if (result.correct) mastered = result.feedback.mastered;
        else {
            retries++;
            queue.push(task);
        }
        if (result.feedback.finished) break;
    }
    done(total);
}

function footer(feedback) {
    const box = el("div");
    if (!feedback.correct) box.append(el("div", "v-fb__retry", "↻ This word comes back until you get it right."));
    const s = feedback.schedule;
    if (s) {
        const line = el("div", "v-fb__next");
        if (s.mastered) line.append("🏆 Mastered — this word won't come back.");
        else if (!s.changed) {
            line.append("⏱ Next review: " + NEXT(s.nextInDays));
            line.append(el("span", "v-boxmove", "stays in box " + s.box));
        } else {
            line.append("⏱ Next review: " + NEXT(s.nextInDays));
            line.append(el("span", "v-boxmove", "box " + s.from + " → " + s.box));
        }
        box.append(line);
    }
    return box.childNodes.length ? box : null;
}

function done(total) {
    guard(false);
    stage("done", 1);
    api.changed();
    const card = el("div", "v-card v-done");
    card.id = "sessionDone";
    if (kind === "learn") {
        const n = session.words.length;
        card.append(el("div", "v-done__icon", "🎉"), el("h1", null, n + " new word" + (n === 1 ? "" : "s") + " learned"),
            el("p", null, "You got every word right at least once."));
        const chips = el("div", "v-chips v-chips--ok");
        chips.append(...session.words.map((w) => el("span", null, "✓ " + w.word)));
        card.append(chips);
    } else {
        card.append(el("div", "v-done__icon", "✅"), el("h1", null, "Review done"),
            el("p", null, "Every due word is reviewed. See you at the next review!"));
    }
    const recap = el("div", "v-recap");
    const line = (label, value) => {
        const row = el("div");
        row.append(el("b", null, label + ": "), document.createTextNode(value));
        return row;
    };
    recap.append(line(kind === "learn" ? "Mastered" : "Reviewed", total + "/" + total + (kind === "learn" ? " tasks" : " words")),
        line("Retries", String(retries)));
    if (kind === "learn") recap.append(line("Next", "these words come back in your daily review tomorrow, then in 3 days, 1 week and 2 weeks"));
    card.append(recap);
    const actions = el("div", "v-actions");
    actions.append(link("My words", "/vocabulary", true));
    card.append(actions);
    body.replaceChildren(card);
}

// ------------------------------------------------------------------ start

async function start() {
    document.title = (kind === "learn" ? "Learn words" : "Word review") + " · Orbiko";
    const user = await currentUser();
    $("sStatus").hidden = true;
    if (!user) {
        message("📚", "Sign in to learn your words", "Your words and reviews are kept in your account.",
            [link("Sign in", "/login?next=" + encodeURIComponent(location.pathname + location.search), true)]);
        return;
    }
    try {
        session = await api.startSession(kind, exercise);
    } catch (e) {
        if (e instanceof ApiError && e.status === 422) {
            message(kind === "learn" ? "📭" : "🌤", kind === "learn" ? "No new words to learn" : "Nothing to review",
                kind === "learn" ? "Save words from a passage, or add some in My words." : "Learned words come back here when they are due.",
                [link("My words", "/vocabulary", true)]);
        } else {
            failed(e);
        }
        return;
    }
    $("sPills").replaceChildren(...STAGES.map(([key, label]) => {
        const li = el("li", "v-pill", label);
        li.dataset.stage = key;
        return li;
    }));
    $("sTop").hidden = false;
    if (kind === "review") {
        $("sBarBox").hidden = true;
        guard(true);
        master();
    } else {
        goal();
    }
}

$("sExit").addEventListener("click", () => {
    if (!inProgress || window.confirm("Leave? Words you haven't finished stay new.")) {
        guard(false);
        location.href = "/vocabulary";
    }
});
start();
