/* =========================================================================
   progress.js — "My progress" (/progress): summary, the learner's attempts
   (newest first, 10 a page, filter by passage) with Review and Try again.
   Signed-out visitors are sent to sign in and come back here.
   ========================================================================= */

import { apiFetch, currentUser } from "./auth.js";
import { coverImage } from "./covers.js";

const PAGE_SIZE = 10;
const $ = (id) => document.getElementById(id);
const state = { page: 0, passage: "" };

function minutes(seconds) {
    if (seconds < 60) return seconds + " s";
    const m = Math.round(seconds / 60);
    return m < 60 ? m + " min" : Math.floor(m / 60) + " h " + (m % 60) + " min";
}

function clock(seconds) {
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
}

function when(iso) {
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })
        + " · " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function grade(percent) {
    return percent >= 80 ? "good" : percent >= 50 ? "ok" : "low";
}

function status(text) {
    $("progressStatus").textContent = text;
    $("progressStatus").hidden = !text;
}

function item(a) {
    const li = document.createElement("li");
    li.className = "attempt";
    li.dataset.id = a.attemptId;

    const cover = document.createElement("div");
    cover.className = "attempt__cover";
    cover.appendChild(coverImage(a.coverUrl, a.topics));

    const body = document.createElement("div");
    body.className = "attempt__body";
    const title = document.createElement("h3");
    title.textContent = a.title;
    const meta = document.createElement("p");
    meta.className = "attempt__meta";
    meta.textContent = when(a.submittedAt) + " · time " + clock(a.elapsedSeconds);
    body.append(title, meta);
    if (!a.available) {
        const gone = document.createElement("p");
        gone.className = "attempt__gone";
        gone.textContent = "This passage isn't available any more.";
        body.appendChild(gone);
    }

    const score = document.createElement("div");
    score.className = "attempt__score attempt__score--" + grade(a.percent);
    const big = document.createElement("b");
    big.textContent = a.score + "/" + a.total;
    const pct = document.createElement("span");
    pct.textContent = a.percent + "%";
    score.append(big, pct);

    const actions = document.createElement("div");
    actions.className = "attempt__actions";
    if (a.available) {
        const review = document.createElement("a");
        review.className = "btn btn--primary";
        review.href = "/reading/" + encodeURIComponent(a.passageId) + "?review=" + encodeURIComponent(a.attemptId);
        review.textContent = "Review";
        const again = document.createElement("a");
        again.className = "btn";
        again.href = "/reading/" + encodeURIComponent(a.passageId);
        again.textContent = "Try again";
        actions.append(review, again);
    }
    li.append(cover, body, score, actions);
    return li;
}

async function loadSummary() {
    const s = await apiFetch("/api/v1/me/progress");
    $("statPassages").textContent = s.passagesDone;
    $("statAttempts").textContent = s.attempts;
    $("statAverage").textContent = s.averagePercent == null ? "–" : s.averagePercent + "%";
    $("statBest").textContent = s.bestPercent == null ? "–" : s.bestPercent + "%";
    $("statTime").textContent = minutes(s.totalSeconds);
    $("stats").hidden = false;
    const filter = $("passageFilter");
    s.passages.forEach((p) => filter.add(new Option(p.title, p.slug)));
    $("historyBar").hidden = s.attempts === 0;
    return s;
}

async function loadPage() {
    const list = $("attemptList");
    list.textContent = "";
    status("Loading…");
    const q = new URLSearchParams({ page: String(state.page), size: String(PAGE_SIZE) });
    if (state.passage) q.set("passageId", state.passage);
    try {
        const page = await apiFetch("/api/v1/me/attempts?" + q);
        page.items.forEach((a) => list.appendChild(item(a)));
        status("");
        const pages = Math.max(1, Math.ceil(page.totalItems / page.size));
        $("pager").hidden = pages <= 1;
        $("pageInfo").textContent = "Page " + (page.page + 1) + " of " + pages;
        $("prevPage").disabled = page.page === 0;
        $("nextPage").disabled = page.page + 1 >= pages;
    } catch (e) {
        status("Couldn't load your attempts. Please try again.");
    }
}

function showEmpty() {
    const p = $("progressStatus");
    p.textContent = "You haven't practised yet. ";
    const go = document.createElement("a");
    go.href = "/reading";
    go.className = "btn btn--primary";
    go.textContent = "Start reading practice";
    p.appendChild(go);
    p.hidden = false;
}

async function start() {
    const user = await currentUser();
    if (!user) {
        location.replace("/login?next=" + encodeURIComponent("/progress"));
        return;
    }
    try {
        const summary = await loadSummary();
        if (summary.attempts === 0) {
            showEmpty();
            return;
        }
    } catch (e) {
        status("Couldn't load your progress. Please try again.");
        return;
    }
    $("passageFilter").addEventListener("change", (e) => { state.passage = e.target.value; state.page = 0; loadPage(); });
    $("prevPage").addEventListener("click", () => { state.page = Math.max(0, state.page - 1); loadPage(); });
    $("nextPage").addEventListener("click", () => { state.page += 1; loadPage(); });
    await loadPage();
}

start();
