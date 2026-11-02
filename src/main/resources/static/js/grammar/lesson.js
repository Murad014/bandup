/* =========================================================================
   grammar/lesson.js — /grammar/lessons/{slug} (grammar requirement 4).
   Goal → Learn cards → worked example → practice → mastery check (a wrong
   item goes to the back of the queue until it is right) → done. Answers
   are graded by the server; the run finishes when the server says every
   check item is mastered. While a lesson runs, leaving asks first.
   ========================================================================= */

import { ApiError, currentUser } from "../auth.js";
import * as api from "./api.js";
import { el, rich, showItem } from "./items.js";

const root = document.getElementById("lessonRoot");
const slug = root.dataset.slug;
const $ = (id) => document.getElementById(id);
const body = $("lbody");
const STAGES = ["goal", "learn", "practice", "check"];

const state = {
    lesson: null, run: null, user: null, stage: "goal", card: 0, inProgress: false,
    seconds: 0, timer: null, mastered: 0, total: 0, retries: 0, finished: false,
};

// ------------------------------------------------------------- page chrome

function setStage(stage) {
    state.stage = stage;
    const at = STAGES.indexOf(stage === "done" ? "check" : stage);
    document.querySelectorAll("#pills .g-pill").forEach((pill) => {
        const i = STAGES.indexOf(pill.dataset.stage);
        pill.classList.toggle("is-active", i === at && stage !== "done");
        pill.classList.toggle("is-done", i < at || stage === "done");
        if (i === at && stage !== "done") pill.setAttribute("aria-current", "step");
        else pill.removeAttribute("aria-current");
    });
}

function progress(fraction) {
    $("lprog").style.width = Math.round(Math.min(1, Math.max(0, fraction)) * 100) + "%";
}

function startClock() {
    stopClock();
    state.timer = setInterval(() => {
        state.seconds += 1;
        $("clock").textContent = clock(state.seconds);
    }, 1000);
}

function stopClock() {
    clearInterval(state.timer);
    state.timer = null;
}

function clock(s) {
    return String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0");
}

function setInProgress(value) {
    state.inProgress = value;
    const nav = window.BandUpNav;
    if (!nav) return;
    if (value) nav.registerGuard(() => state.inProgress);
    else nav.clearGuard();
}

function button(label, className, onClick) {
    const b = el("button", "btn " + (className || ""), label);
    b.type = "button";
    b.addEventListener("click", onClick);
    return b;
}

function message(title, text, actions) {
    const card = el("div", "g-card g-msg");
    card.append(el("h1", null, title), el("p", null, text));
    const row = el("div", "g-msg__actions");
    row.append(...actions);
    card.append(row);
    body.replaceChildren(card);
}

function backLink(label = "Back to course") {
    const a = el("a", "btn", label);
    a.href = "/grammar";
    return a;
}

function failed(error) {
    stopClock();
    setInProgress(false);
    if (error instanceof ApiError && error.status === 409) {
        message("This lesson changed", error.message, [button("Start again", "btn--primary", () => location.reload()), backLink()]);
    } else if (error instanceof ApiError && error.status === 404) {
        message("Lesson not found", "This lesson isn't available any more.", [backLink()]);
    } else {
        message("Something went wrong", "We couldn't reach Orbiko. Check your connection and try again.",
            [button("Try again", "btn--primary", () => location.reload()), backLink()]);
    }
}

// -------------------------------------------------------------------- goal

function showGoal() {
    setStage("goal");
    progress(0.02);
    const card = el("div", "g-card g-goal");
    card.append(el("div", "g-goal__icon", "🎯"), el("h1", null, state.lesson.title), el("p", null, state.lesson.goal));
    const meta = el("p", "g-goal__meta",
        "⏱ About " + state.lesson.minutes + " minutes · " + state.lesson.learn.length + " cards · "
        + state.lesson.practiceCount + " practice · " + state.lesson.checkCount + " to master");
    const go = button("📖 Start learning", "btn--primary btn--lg", startLearning);
    go.id = "startLearning";
    card.append(meta, go);
    body.replaceChildren(card);
    go.focus();
}

async function startLearning() {
    const go = $("startLearning");
    go.disabled = true;
    try {
        state.run = await api.startRun(slug);
    } catch (e) {
        failed(e);
        return;
    }
    state.total = state.run.check.length;
    setInProgress(true);
    startClock();
    state.card = 0;
    showCard();
}

// ------------------------------------------------------------------- learn

function showCard() {
    setStage("learn");
    const cards = state.lesson.learn;
    const c = cards[state.card];
    const last = state.card === cards.length - 1;
    progress(0.05 + 0.3 * (state.card / cards.length));
    const card = el("div", "g-card g-teach");
    card.dataset.card = String(state.card);
    card.append(el("div", "g-teach__icon", c.icon),
        el("div", "g-teach__label", c.label + " · " + (state.card + 1) + "/" + cards.length),
        el("h2", null, c.heading));
    if (c.body) card.append(rich(c.body, "g-teach__body"));
    if (c.steps.length) {
        const steps = el("div", "g-steps");
        steps.append(...c.steps.map((s) => rich(s, "g-step")));
        card.append(steps);
    }
    for (const ex of c.examples) {
        const line = el("div", "g-ex " + (ex.ok ? "g-ex--ok" : "g-ex--no"));
        line.append(el("span", "g-ex__mark", ex.ok ? "✓" : "✗"), rich(ex.text));
        card.append(line);
    }
    if (c.tip) {
        const tip = el("div", "g-tip");
        tip.append("💡 ", rich(c.tip));
        card.append(tip);
    }
    const nav = el("div", "g-teach__nav");
    if (state.card > 0) {
        nav.append(button("← Back", "", () => {
            state.card -= 1;
            showCard();
        }));
    }
    const next = button(last ? "✅ Got it — practise now" : "Next →", "btn--primary g-grow", () => {
        if (last) showWorked();
        else {
            state.card += 1;
            showCard();
        }
    });
    next.id = "learnNext";
    nav.append(next);
    card.append(el("div", "g-spacer"), nav);
    body.replaceChildren(card);
    next.focus();
}

// ---------------------------------------------------------------- practice

function showWorked() {
    setStage("practice");
    progress(0.37);
    const w = state.lesson.worked;
    const card = el("div", "g-card g-q g-worked");
    const kick = el("div", "g-q__kick");
    kick.append(el("span", null, "👀 Worked example"), el("span", "g-q__type", "watch the thinking"));
    const steps = el("div", "g-steps");
    steps.append(...w.steps.map((s) => rich(s, "g-step")));
    const answer = el("div", "g-worked__answer", "✅ Answer: ");
    answer.append(el("b", null, w.answer));
    const go = button("Now you try →", "btn--primary g-wide", () => practice(0));
    go.id = "workedNext";
    card.append(kick, rich(w.question, "g-q__prompt"), steps, answer, go);
    body.replaceChildren(card);
    go.focus();
}

async function practice(i) {
    const items = state.run.practice;
    if (i >= items.length) {
        check();
        return;
    }
    setStage("practice");
    progress(0.4 + 0.2 * (i / items.length));
    const last = i === items.length - 1;
    try {
        await showItem(body, items[i], {
            kick: "✏️ Guided practice · " + (i + 1) + " of " + items.length,
            nextLabel: last ? "Start the mastery check →" : "Next →",
            answer: (value) => api.answer(state.run.runId, items[i].id, value),
        });
    } catch (e) {
        failed(e);
        return;
    }
    practice(i + 1);
}

// ------------------------------------------------------------------ master

async function check() {
    const queue = [...state.run.check];
    while (queue.length) {
        setStage("check");
        progress(0.6 + 0.4 * (state.mastered / state.total));
        const item = queue[0];
        let result;
        try {
            result = await showItem(body, item, {
                kick: "🎯 Prove it · " + (state.mastered + 1) + " of " + state.total,
                header: masteryBar(),
                nextLabel: "Continue →",
                answer: async (value) => {
                    const feedback = await api.answer(state.run.runId, item.id, value);
                    state.mastered = feedback.mastered;
                    state.retries = feedback.retries;
                    state.finished = feedback.finished;
                    return feedback;
                },
                footer: (feedback) => feedback.correct ? null
                    : el("div", "g-fb__retry", "↻ This comes back until you get it right."),
            });
        } catch (e) {
            failed(e);
            return;
        }
        queue.shift();
        if (!result.correct) queue.push(item);
        if (state.finished) break;
    }
    done();
}

function masteryBar() {
    const bar = el("div", "g-mastery");
    const track = el("div", "g-mastery__track");
    const fill = el("i");
    fill.style.width = Math.round((state.mastered / state.total) * 100) + "%";
    track.append(fill);
    const count = el("b", null, state.mastered + " / " + state.total);
    count.id = "masteryCount";
    bar.append(el("span", null, "Mastery"), track, count);
    return bar;
}

// -------------------------------------------------------------------- done

async function done() {
    stopClock();
    setInProgress(false);
    setStage("done");
    progress(1);
    const card = el("div", "g-card g-done");
    card.id = "lessonDone";
    card.append(el("div", "g-done__icon", "🎉"), el("h1", null, "Lesson complete — 100%"),
        el("p", null, "“" + state.lesson.title + "” mastered."));
    const recap = el("div", "g-recap");
    const line = (label, value) => {
        const row = el("div");
        row.append(el("b", null, label + ": "), document.createTextNode(value));
        return row;
    };
    recap.append(line("Time", clock(state.seconds)),
        line("Mastered", state.total + "/" + state.total + " items"),
        line("Retries", String(state.retries)));
    const nextLine = line("Next", "…");
    recap.append(nextLine);
    recap.append(line("Review", state.user ? "these items come back in your daily review"
        : "sign in to keep your progress and get a daily review"));
    card.append(recap);
    const actions = el("div", "g-msg__actions");
    actions.append(backLink());
    if (!state.user) {
        const signIn = el("a", "btn btn--primary", "Sign in");
        signIn.href = "/login?next=" + encodeURIComponent("/grammar");
        actions.prepend(signIn);
    }
    card.append(actions);
    body.replaceChildren(card);
    if (!state.user) {
        nextLine.lastChild.textContent = "sign in to unlock the next lessons";
        return;
    }
    try {
        const course = await api.course(state.lesson.level, { size: 100 });
        const next = course.next;
        const entry = next && course.lessons.find((l) => l.slug === next.slug);
        nextLine.lastChild.textContent = !next ? "you've finished this course — new lessons arrive regularly"
            : next.title + (entry && entry.state === "NEXT" ? " (ready now)" : " (unlocks tomorrow)");
    } catch (e) {
        nextLine.remove();
    }
}

// ------------------------------------------------------------------- start

async function start() {
    try {
        const [lesson, user] = await Promise.all([api.lesson(slug), currentUser()]);
        state.lesson = lesson;
        state.user = user;
    } catch (e) {
        $("lstatus").hidden = true;
        if (e instanceof ApiError && e.status === 401) {
            const signIn = el("a", "btn btn--primary", "Sign in");
            signIn.href = "/login?next=" + encodeURIComponent(location.pathname);
            message("Sign in to continue", "Lesson 1 of each level is free. Sign in to unlock the rest of the course.",
                [signIn, backLink()]);
        } else if (e instanceof ApiError && e.status === 403) {
            message("Not unlocked yet", e.message, [backLink()]);
        } else {
            failed(e);
        }
        return;
    }
    document.title = state.lesson.title + " · Orbiko";
    $("lstatus").hidden = true;
    $("lshell").hidden = false;
    showGoal();
}

$("exitLesson").addEventListener("click", () => {
    if (!state.inProgress || window.confirm("Leave the lesson? Your progress in it will be lost.")) {
        setInProgress(false);
        location.href = "/grammar";
    }
});
start();
