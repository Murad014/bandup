import { test } from "node:test";
import assert from "node:assert/strict";
import { JSDOM } from "jsdom";

import { createReadingApp } from "../src/main/resources/static/js/reading/app.js";
import { stubApi } from "../src/main/resources/static/js/reading/api.js";

const SHELL = `<!DOCTYPE html><html><body>
<main class="reading" id="reading-root" data-session-id="yellowstone-wolves" data-api-base="/api/v1">
  <div class="reading-bar"><div class="bar">
    <h1><small id="stageLabel"></small><span id="passageTitle"></span></h1>
    <span class="timer" id="timer">--:--</span>
    <button class="btn btn--primary" id="submit" type="button" disabled>Submit</button>
  </div></div>
  <div class="reading-main">
    <section class="panel passage" aria-label="Reading passage">
      <p class="label">Reading passage</p>
      <div id="passage" class="panel__status">Loading…</div>
      <details class="vocab" hidden><summary>Word list</summary><div class="vlist" id="vlist"></div></details>
    </section>
    <section class="panel" aria-label="Questions">
      <div id="resultBox" hidden></div>
      <form id="form" autocomplete="off">
        <div id="questions" class="panel__status">Loading…</div>
        <div class="actions">
          <button class="btn btn--primary" type="submit" disabled>Submit answers</button>
          <button class="btn" type="button" id="reset" disabled>Start again</button>
        </div>
      </form>
    </section>
  </div>
  <button id="hlBtn" type="button" hidden>Highlight</button>
  <div id="pop" hidden></div>
</main></body></html>`;

function makeApp(api = stubApi) {
    const dom = new JSDOM(SHELL, { url: "http://localhost/reading/yellowstone-wolves" });
    const { window } = dom;
    const document = window.document;
    const nav = {
        _guard: null,
        registerGuard(fn) { this._guard = fn; },
        clearGuard() { this._guard = null; },
    };
    window.BandUpNav = nav;
    const app = createReadingApp({ api, doc: document, win: window, autoTick: false });
    return { app, window, document, nav };
}

const PERFECT = {
    q1: "FALSE", q2: "TRUE", q3: "NOT GIVEN",
    in4: "riverbanks", in5: "songbirds", in6: "wolves",
};

function fillPerfect(document) {
    document.querySelector('input[name="q1"][value="FALSE"]').checked = true;
    document.querySelector('input[name="q2"][value="TRUE"]').checked = true;
    document.querySelector('input[name="q3"][value="NOT GIVEN"]').checked = true;
    document.getElementById("in4").value = PERFECT.in4;
    document.getElementById("in5").value = PERFECT.in5;
    document.getElementById("in6").value = PERFECT.in6;
}

test("init renders passage, word list, and questions", async () => {
    const { app, document } = makeApp();
    await app.init();

    const words = document.querySelectorAll("#passage .w");
    const rows = document.querySelectorAll("#vlist div");
    assert.ok(words.length > 0, "expected glossed words");
    assert.equal(rows.length, words.length, "word list rows match gloss count");
    assert.equal(document.querySelector(".vocab").hidden, false);

    assert.equal(document.querySelectorAll('.opts[role="radiogroup"]').length, 3);
    assert.equal(document.querySelectorAll("input.fill").length, 3);
    assert.equal(document.getElementById("stageLabel").textContent, "Stage 1 · Mini passage 4");
    assert.equal(document.getElementById("timer").textContent, "5:00");
    assert.equal(document.getElementById("submit").disabled, false);
});

test("init starts in-progress and registers the navigation guard", async () => {
    const { app, nav } = makeApp();
    await app.init();
    assert.equal(app.isInProgress(), true);
    assert.equal(typeof nav._guard, "function");
    assert.equal(nav._guard(), true);
});

test("timer ticks down, flips low near the threshold, and counts into overtime", async () => {
    const { app, document } = makeApp();
    await app.init();
    app.tick();
    assert.equal(document.getElementById("timer").textContent, "4:59");

    app._state.timer.remaining = 61;
    app.tick(); // -> 60
    assert.equal(document.getElementById("timer").textContent, "1:00");
    assert.equal(document.getElementById("timer").classList.contains("low"), true);

    app._state.timer.remaining = 0;
    app.tick(); // -> -1
    assert.equal(document.getElementById("timer").textContent, "+0:01");
});

test("tapping a glossed word opens the popover; tapping outside closes it", async () => {
    const { app, window, document } = makeApp();
    await app.init();
    const word = document.querySelector("#passage .w");
    word.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
    const pop = document.getElementById("pop");
    assert.equal(pop.hidden, false);
    assert.ok(pop.textContent.includes(word.dataset.az));

    document.body.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
    assert.equal(pop.hidden, true);
});

test("highlight wraps a selection in <mark>; clicking the mark unwraps it", async () => {
    const { app, window, document } = makeApp();
    await app.init();
    const passage = document.getElementById("passage");

    // Pick a top-level text node (not inside a .w) so clicking the mark unwraps.
    let textNode = null;
    for (const n of passage.childNodes) {
        if (n.nodeType === 3 && n.textContent.trim().length > 3) { textNode = n; break; }
    }
    assert.ok(textNode, "found a plain text node to highlight");
    const original = textNode.textContent;

    const range = document.createRange();
    range.selectNode(textNode);
    app._applyHighlight(range);

    const mark = passage.querySelector("mark");
    assert.ok(mark, "a <mark> was created");
    assert.equal(mark.textContent, original);

    mark.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
    assert.equal(passage.querySelector("mark"), null, "mark removed on click");
});

test("submit grades 6/6, shows feedback, disables inputs, ends in-progress", async () => {
    const { app, document, nav } = makeApp();
    await app.init();
    fillPerfect(document);

    await app.submit();

    assert.equal(document.getElementById("resultBox").hidden, false);
    assert.equal(document.querySelector("#resultBox .big").textContent, "6/6");
    assert.equal(document.querySelectorAll(".q.correct").length, 6);
    assert.equal(document.querySelectorAll(".q input:disabled").length > 0, true);
    assert.equal(app.isInProgress(), false);
    assert.equal(nav._guard, null, "nav guard cleared after submit");

    // Double-submit guard: a second submit must not throw or change the result.
    await app.submit();
    assert.equal(document.querySelector("#resultBox .big").textContent, "6/6");
});

test("Show-in-text highlights the evidence span and clears the previous one", async () => {
    const { app, window, document } = makeApp();
    await app.init();
    fillPerfect(document);
    await app.submit();

    const evBtn = document.querySelector("#q1 [data-ev]");
    assert.ok(evBtn, "feedback has a Show-in-text button");
    evBtn.dispatchEvent(new window.MouseEvent("click", { bubbles: true }));
    assert.equal(document.getElementById("e1").classList.contains("on"), true);

    app.showEvidence("e2");
    assert.equal(document.getElementById("e1").classList.contains("on"), false);
    assert.equal(document.getElementById("e2").classList.contains("on"), true);
});

test("restart returns the screen to a fresh attempt", async () => {
    const { app, document, nav } = makeApp();
    await app.init();
    fillPerfect(document);
    await app.submit();

    app.restart();

    assert.equal(document.getElementById("resultBox").hidden, true);
    assert.equal(document.querySelectorAll(".q.correct").length, 0);
    assert.equal(document.querySelectorAll(".q input:disabled").length, 0);
    assert.equal(document.getElementById("timer").textContent, "5:00");
    assert.equal(app._state.timer.remaining, 300);
    assert.equal(app.isInProgress(), true);
    assert.equal(typeof nav._guard, "function");
    assert.equal(document.querySelectorAll("#passage mark").length, 0);
});

test("init shows an error + retry when the session fails to load", async () => {
    const failing = {
        fetchSession: async () => { const e = new Error("boom"); e.status = 500; throw e; },
        submitAttempt: async () => ({}),
    };
    const { app, document } = makeApp(failing);
    await app.init();
    assert.ok(document.getElementById("passage").textContent.toLowerCase().includes("couldn't load"));
    assert.ok(document.getElementById("retry"), "retry button is shown");
    assert.equal(document.getElementById("submit").disabled, true);
});
