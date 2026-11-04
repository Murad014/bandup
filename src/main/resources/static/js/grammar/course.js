/* =========================================================================
   grammar/course.js — /grammar (grammar requirements 1–3, design §6).
   First visit: choose a level (signed in → saved on the server; guest →
   kept on this device). Then: the course of that level with the review
   card, today's lesson and the lesson list (DONE / NEXT / LOCKED).
   Every text from the API goes in with textContent.
   ========================================================================= */

import { currentUser } from "../auth.js";
import * as api from "./api.js";

const LEVEL_KEY = "bandup.grammar.level";
const PAGE_SIZE = 50;
const LEVELS = {
    A2: { icon: "🌱", tone: "teal", sub: "Foundations course" },
    B1: { icon: "🌿", tone: "sky", sub: "Core IELTS grammar" },
    B2: { icon: "🌳", tone: "grape", sub: "Band 7+ structures" },
    C1: { icon: "🏔️", tone: "sun", sub: "Band 8+ structures" },
};
const LOCKED_TEXT = {
    TOMORROW: "Unlocks tomorrow",
    FINISH_PREVIOUS: "Finish the earlier lessons first",
    SIGN_IN: "Sign in to continue",
};

const $ = (id) => document.getElementById(id);
const state = { user: null, levels: [], level: null, topic: null, page: 0, total: 0, data: null };

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

function savedLevel() {
    try {
        return localStorage.getItem(LEVEL_KEY);
    } catch (e) {
        return null;
    }
}

function keepLevel(level) {
    try {
        localStorage.setItem(LEVEL_KEY, level);
    } catch (e) {
        /* private mode: the level just isn't remembered */
    }
}

function status(text, retry) {
    const box = $("gStatus");
    box.replaceChildren(el("span", null, text));
    if (retry) {
        const again = el("button", "btn", "Try again");
        again.type = "button";
        again.addEventListener("click", start);
        box.append(" ", again);
    }
    box.hidden = !text;
}

// ------------------------------------------------------------- level choice

function showLevelPick() {
    $("course").hidden = true;
    const list = $("levelList");
    list.replaceChildren();
    for (const level of state.levels) {
        const look = LEVELS[level.level] || { icon: "📘", tone: "sky", sub: "" };
        const open = level.lessonCount > 0;
        const card = el("button", "g-level g-level--" + look.tone + (level.level === state.level ? " is-current" : ""));
        card.type = "button";
        card.dataset.level = level.level;
        card.disabled = !open;
        card.append(el("span", "g-level__icon", look.icon));
        const text = el("span", "g-level__text");
        text.append(el("b", null, level.name),
            el("small", null, open ? look.sub + " · " + level.lessonCount + (level.lessonCount === 1 ? " lesson" : " lessons")
                : "Coming soon"));
        card.append(text, el("span", "g-level__code", level.level));
        card.addEventListener("click", () => pick(level.level));
        list.append(card);
    }
    $("levelPick").hidden = false;
    status("");
}

async function pick(level) {
    keepLevel(level);
    state.level = level;
    if (state.user) {
        try {
            await api.chooseLevel(level);
        } catch (e) {
            /* still show the course; the level is kept on this device */
        }
    }
    state.topic = null;
    await showCourse();
}

// ------------------------------------------------------------------- course

async function showCourse() {
    $("levelPick").hidden = true;
    status("Loading your course…");
    try {
        state.page = 0;
        state.data = await api.course(state.level, { topic: state.topic, page: 0, size: PAGE_SIZE });
    } catch (e) {
        status("We couldn't load the course. Check your connection.", true);
        return;
    }
    renderCourse(state.data);
    status("");
    $("course").hidden = false;
}

function renderCourse(data) {
    const level = state.levels.find((l) => l.level === data.level);
    $("courseTitle").textContent = data.level + " · " + (level ? level.name : "") + " grammar";
    const position = Math.min(data.finished + 1, Math.max(data.available, 1));
    $("courseSub").textContent = data.available === 0 ? "No lessons yet"
        : data.finished >= data.available ? "All " + data.available + " lessons done"
            : "Lesson " + position + " · " + data.available + " available";
    $("courseBar").style.width = (data.available ? Math.round((data.finished / data.available) * 100) : 0) + "%";

    renderReview(data.review);
    $("guestCard").hidden = !!state.user;
    renderToday(data);
    renderTopics(data.topics);
    $("lessonList").replaceChildren(...data.lessons.map(row));
    $("lessonEmpty").hidden = data.lessons.length > 0;
    state.total = data.total;
    $("moreLessons").hidden = data.lessons.length >= data.total;
}

function renderReview(review) {
    const card = $("reviewCard");
    if (!review) {
        card.hidden = true;
        return;
    }
    card.hidden = false;
    const due = review.due;
    $("reviewGo").hidden = due === 0;
    $("reviewSub").textContent = due > 0 ? due + (due === 1 ? " item" : " items") + " due from earlier lessons · ~" + Math.max(1, Math.ceil(due / 2)) + " min"
        : review.nextDueOn ? "Nothing due today · next review " + dayText(review.nextDueOn, review.nextInDays)
            : "Finish a lesson and its rules come back here to review.";
}

/** @param days counted by the server from its today (the browser's clock may be on another day) */
function dayText(iso, days) {
    if (days === 1) return "tomorrow";
    const [y, m, d] = iso.split("-").map(Number);
    return "on " + new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "short" });
}

function renderToday(data) {
    const next = data.next;
    $("allDone").hidden = !(data.available > 0 && !next);
    if (!next) {
        $("todayCard").hidden = true;
        return;
    }
    const entry = data.lessons.find((l) => l.slug === next.slug);
    const waiting = entry && entry.state === "LOCKED" && entry.lockedReason === "TOMORROW";
    $("todayCard").hidden = false;
    $("todayKick").textContent = waiting ? "Tomorrow's lesson" : "Today's lesson";
    $("todayTitle").textContent = next.title;
    $("todayGo").hidden = waiting;
    $("todayGo").href = api.lessonUrl(next.slug);
    $("todayDone").hidden = !waiting;
}

function renderTopics(topics) {
    const box = $("topicChips");
    box.replaceChildren();
    if (!topics || topics.length < 2) return;
    const all = [{ key: null, label: "All topics" }, ...topics];
    for (const topic of all) {
        const chip = el("button", "g-topic" + (state.topic === topic.key ? " is-on" : ""), topic.label);
        chip.type = "button";
        chip.dataset.topic = topic.key || "";
        chip.setAttribute("aria-pressed", String(state.topic === topic.key));
        chip.addEventListener("click", () => {
            state.topic = topic.key;
            showCourse();
        });
        box.append(chip);
    }
}

function row(lesson) {
    const open = lesson.state !== "LOCKED";
    const item = el("li", "g-day g-day--" + lesson.state.toLowerCase());
    item.dataset.slug = lesson.slug;
    item.dataset.state = lesson.state;
    const inner = el(open ? "a" : "div", "g-day__inner");
    if (open) inner.href = api.lessonUrl(lesson.slug);
    inner.append(el("span", "g-day__n", String(lesson.position)));
    const text = el("span", "g-day__text");
    const sub = lesson.state === "DONE" ? "Done · replay any time"
        : lesson.state === "NEXT" ? "Up next · ~" + lesson.minutes + " min"
            : LOCKED_TEXT[lesson.lockedReason] || "Locked";
    text.append(el("b", null, lesson.title), el("small", null, lesson.topic.label + " · " + sub));
    inner.append(text, el("span", "g-day__state", lesson.state === "DONE" ? "✓" : lesson.state === "NEXT" ? "▶" : "🔒"));
    item.append(inner);
    return item;
}

async function more() {
    const button = $("moreLessons");
    button.disabled = true;
    try {
        const page = await api.course(state.level, { topic: state.topic, page: state.page + 1, size: PAGE_SIZE });
        state.page += 1;
        $("lessonList").append(...page.lessons.map(row));
        button.hidden = $("lessonList").children.length >= page.total;
    } catch (e) {
        /* the button stays for another try */
    } finally {
        button.disabled = false;
    }
}

// -------------------------------------------------------------------- start

async function start() {
    status("Loading…");
    try {
        const [user, levels] = await Promise.all([currentUser(), api.levels()]);
        state.user = user;
        state.levels = levels;
    } catch (e) {
        status("We couldn't load the grammar course. Check your connection.", true);
        return;
    }
    let level = new URLSearchParams(location.search).get("level");
    if (state.user) {
        try {
            const mine = await api.me();
            level = level || mine.level;
            if (!mine.level && savedLevel()) {
                level = level || savedLevel();
                await api.chooseLevel(level); // a level picked as a guest is kept after signing in
            }
        } catch (e) {
            /* fall back to this device's level */
        }
    }
    level = level || savedLevel();
    if (level && state.levels.some((l) => l.level === level)) {
        state.level = level;
        keepLevel(level);
        await showCourse();
    } else {
        showLevelPick();
    }
}

$("changeLevel").addEventListener("click", showLevelPick);
$("moreLessons").addEventListener("click", more);
start();
