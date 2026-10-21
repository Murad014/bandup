/* =========================================================================
   app.js — Reading Practice DOM controller.

   createReadingApp({ api, doc, win, autoTick }) returns a controller with:
     init()            load session, render, start timer, wire events
     submit()          grade via the API and show feedback
     restart()         reset to a fresh attempt
     showEvidence(id)  highlight a passage evidence span
     isInProgress()    for the app-shell navigation guard
     tick()            advance the timer one second (tests drive this)

   `doc`/`win` are injectable so the controller can run under jsdom in tests.
   All session/explanation HTML is treated as trusted (server-sanitized);
   learner- or data-derived text is written with textContent.
   ========================================================================= */

import { formatClock, isLowTime } from "./core.js";

export function createReadingApp(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const api = options.api;
    const autoTick = options.autoTick !== false;

    const els = {};
    const state = {
        session: null,
        graded: false,
        submitting: false,
        inProgress: false,
        timer: { remaining: 0, threshold: 60, intervalId: null, running: false },
    };
    let savedRange = null;

    const byId = (id) => d.getElementById(id);
    const selection = () => (w.getSelection ? w.getSelection() : null);
    const safeScroll = (el) => {
        if (el && typeof el.scrollIntoView === "function") {
            try { el.scrollIntoView({ behavior: "smooth", block: "center" }); } catch (e) { /* jsdom noop */ }
        }
    };

    function cacheEls() {
        els.root = byId("reading-root");
        els.stageLabel = byId("stageLabel");
        els.title = byId("passageTitle");
        els.timer = byId("timer");
        els.submitTop = byId("submit");
        els.passage = byId("passage");
        els.vocab = d.querySelector(".vocab");
        els.vlist = byId("vlist");
        els.questions = byId("questions");
        els.form = byId("form");
        els.resultBox = byId("resultBox");
        els.reset = byId("reset");
        els.hlBtn = byId("hlBtn");
        els.pop = byId("pop");
        els.formSubmitBtn = els.form ? els.form.querySelector('button[type="submit"]') : null;
    }

    /* ---- lifecycle ---------------------------------------------------- */
    async function init() {
        cacheEls();
        if (!els.root) return;
        wireEvents();
        showLoading();
        const sessionId = els.root.dataset.sessionId;
        try {
            const session = await api.fetchSession(sessionId);
            state.session = session;
            renderSession(session);
            startTimer(session.durationSeconds, session.lowTimeThresholdSeconds);
            setControls({ submit: true, reset: true });
            setInProgress(true);
        } catch (err) {
            showError(err);
        }
    }

    function showLoading() {
        if (els.passage) { els.passage.classList.add("panel__status"); els.passage.textContent = "Loading passage…"; }
        if (els.questions) { els.questions.classList.add("panel__status"); els.questions.textContent = "Loading questions…"; }
        setControls({ submit: false, reset: false });
    }

    function showError(err) {
        const message = "Couldn't load this reading session. " + (err && err.status === 404 ? "It may not exist." : "Please try again.");
        if (els.passage) {
            els.passage.classList.add("panel__status");
            els.passage.textContent = message + " ";
            const retry = d.createElement("button");
            retry.type = "button";
            retry.className = "btn";
            retry.id = "retry";
            retry.textContent = "Retry";
            retry.addEventListener("click", () => init());
            els.passage.appendChild(retry);
        }
        if (els.questions) { els.questions.classList.add("panel__status"); els.questions.textContent = ""; }
        if (els.timer) els.timer.textContent = "--:--";
        setControls({ submit: false, reset: false });
    }

    /* ---- rendering ---------------------------------------------------- */
    function renderSession(session) {
        if (els.stageLabel) els.stageLabel.textContent = session.stageLabel || "";
        if (els.title) els.title.textContent = session.title || "";
        renderPassage(session);
        buildWordList();
        renderQuestions(session);
    }

    function renderPassage(session) {
        els.passage.classList.remove("panel__status");
        // Trusted, server-sanitized HTML fragment.
        els.passage.innerHTML = session.passageHtml || "";
        ensureTips();
    }

    function ensureTips() {
        const panel = els.passage ? els.passage.parentNode : null;
        if (!panel || panel.querySelector(".tips")) return;
        const tips = d.createElement("p");
        tips.className = "tips";
        tips.textContent = "Select text to highlight it. Tap a highlight to remove it. Tap a dotted word for the Azerbaijani meaning.";
        panel.insertBefore(tips, els.passage);
    }

    function buildWordList() {
        if (!els.vlist) return;
        els.vlist.textContent = "";
        const words = els.passage.querySelectorAll(".w");
        words.forEach((wEl) => {
            const row = d.createElement("div");
            const term = d.createElement("span");
            term.textContent = wEl.textContent;
            const gloss = d.createElement("span");
            gloss.textContent = wEl.dataset.az || "";
            row.appendChild(term);
            row.appendChild(gloss);
            els.vlist.appendChild(row);
        });
        if (els.vocab) els.vocab.hidden = words.length === 0;
    }

    function renderQuestions(session) {
        els.questions.classList.remove("panel__status");
        els.questions.textContent = "";
        (session.questionGroups || []).forEach((group) => {
            const groupEl = d.createElement("div");
            groupEl.className = "qgroup";

            const heading = d.createElement("h3");
            heading.textContent = group.heading || "";
            groupEl.appendChild(heading);

            const instr = d.createElement("p");
            instr.className = "instr";
            instr.innerHTML = group.instruction || ""; // trusted authored content
            groupEl.appendChild(instr);

            (group.questions || []).forEach((q) => groupEl.appendChild(renderQuestion(q)));
            els.questions.appendChild(groupEl);
        });
    }

    function renderQuestion(q) {
        const box = d.createElement("div");
        box.className = "q";
        box.id = "q" + q.n;

        const stem = d.createElement("div");
        stem.className = "stem";
        const num = d.createElement("span");
        num.className = "num";
        num.textContent = String(q.n);
        stem.appendChild(num);

        if (q.type === "TFNG") {
            const text = d.createElement("span");
            text.textContent = q.text || "";
            stem.appendChild(text);
            box.appendChild(stem);

            const opts = d.createElement("div");
            opts.className = "opts";
            opts.setAttribute("role", "radiogroup");
            opts.setAttribute("aria-label", "Question " + q.n);
            ["TRUE", "FALSE", "NOT GIVEN"].forEach((value, i) => {
                const label = d.createElement("label");
                const input = d.createElement("input");
                input.type = "radio";
                input.name = "q" + q.n;
                input.value = value;
                input.id = "q" + q.n + "o" + i;
                label.appendChild(input);
                label.appendChild(d.createTextNode(value));
                opts.appendChild(label);
            });
            box.appendChild(opts);
        } else {
            // FILL
            const text = d.createElement("span");
            text.appendChild(d.createTextNode((q.pre || "") + " "));
            const input = d.createElement("input");
            input.className = "fill";
            input.name = "q" + q.n;
            input.id = "in" + q.n;
            input.setAttribute("aria-label", "Answer " + q.n);
            input.setAttribute("autocapitalize", "off");
            input.spellcheck = false;
            text.appendChild(input);
            text.appendChild(d.createTextNode(" " + (q.post || "")));
            stem.appendChild(text);
            box.appendChild(stem);
        }

        const fb = d.createElement("div");
        fb.className = "fb";
        fb.hidden = true;
        box.appendChild(fb);
        return box;
    }

    /* ---- timer -------------------------------------------------------- */
    function startTimer(duration, threshold) {
        state.timer.remaining = duration;
        state.timer.threshold = threshold == null ? 60 : threshold;
        renderTimer();
        clearTimerInterval();
        state.timer.running = true;
        if (autoTick && w.setInterval) {
            state.timer.intervalId = w.setInterval(() => tick(), 1000);
        }
    }
    function tick() {
        if (!state.timer.running) return;
        state.timer.remaining -= 1;
        renderTimer();
    }
    function renderTimer() {
        if (!els.timer) return;
        els.timer.textContent = formatClock(state.timer.remaining);
        els.timer.classList.toggle("low", isLowTime(state.timer.remaining, state.timer.threshold));
    }
    function clearTimerInterval() {
        if (state.timer.intervalId != null && w.clearInterval) w.clearInterval(state.timer.intervalId);
        state.timer.intervalId = null;
    }
    function stopTimer() {
        state.timer.running = false;
        clearTimerInterval();
    }
    function resetTimer() {
        stopTimer();
        if (state.session) startTimer(state.session.durationSeconds, state.session.lowTimeThresholdSeconds);
    }

    /* ---- gloss popover ------------------------------------------------ */
    function showPopover(wordEl) {
        if (!els.pop) return;
        els.pop.textContent = "";
        const term = d.createElement("b");
        term.textContent = wordEl.textContent;
        els.pop.appendChild(term);
        els.pop.appendChild(d.createTextNode(wordEl.dataset.az || ""));
        place(els.pop, wordEl);
    }
    function place(el, anchor) {
        el.hidden = false;
        if (!anchor.getBoundingClientRect) return;
        const r = anchor.getBoundingClientRect();
        const width = el.offsetWidth || 0;
        const viewport = (d.documentElement && d.documentElement.clientWidth) || 0;
        const max = Math.max(8, viewport - width - 8);
        const x = r.left + r.width / 2 - width / 2;
        el.style.left = Math.max(8, Math.min(x, max)) + "px";
        el.style.top = (r.bottom + (w.scrollY || 0) + 8) + "px";
    }

    /* ---- highlighting ------------------------------------------------- */
    function checkSelection() {
        const s = selection();
        if (!s || !s.rangeCount || s.isCollapsed) { hideHighlightButton(); return; }
        const range = s.getRangeAt(0);
        if (!els.passage.contains(range.commonAncestorContainer)) { hideHighlightButton(); return; }
        savedRange = range.cloneRange();
        place(els.hlBtn, { getBoundingClientRect: () => range.getBoundingClientRect() });
        if (els.pop) els.pop.hidden = true;
    }
    function hideHighlightButton() { if (els.hlBtn) els.hlBtn.hidden = true; }
    function applyHighlight(range) {
        const mark = d.createElement("mark");
        mark.appendChild(range.extractContents());
        range.insertNode(mark);
        return mark;
    }
    function highlightSavedSelection() {
        if (!savedRange) return;
        applyHighlight(savedRange);
        const s = selection();
        if (s && s.removeAllRanges) s.removeAllRanges();
        hideHighlightButton();
        savedRange = null;
    }
    function unwrap(mark) {
        const parent = mark.parentNode;
        while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
        mark.remove();
        parent.normalize();
    }

    /* ---- evidence ----------------------------------------------------- */
    function showEvidence(evidenceId) {
        d.querySelectorAll(".ev.on").forEach((x) => x.classList.remove("on"));
        const target = byId(evidenceId);
        if (target) { target.classList.add("on"); safeScroll(target); }
    }

    /* ---- submit + grading -------------------------------------------- */
    function collectAnswers() {
        const out = [];
        (state.session.questionGroups || []).forEach((g) =>
            g.questions.forEach((q) => {
                let value = "";
                if (q.type === "TFNG") {
                    const checked = d.querySelector('input[name="q' + q.n + '"]:checked');
                    value = checked ? checked.value : "";
                } else {
                    const input = byId("in" + q.n);
                    value = input ? input.value.trim() : "";
                }
                out.push({ n: q.n, value });
            })
        );
        return out;
    }

    async function submit(e) {
        if (e && e.preventDefault) e.preventDefault();
        if (state.submitting || state.graded || !state.session) return;
        state.submitting = true;
        const answers = collectAnswers();
        const elapsed = Math.max(0, state.session.durationSeconds - state.timer.remaining);
        try {
            const result = await api.submitAttempt({
                passageId: state.session.id,
                elapsedSeconds: elapsed,
                answers,
            });
            applyGrades(result);
            state.graded = true;
            stopTimer();
            setInProgress(false);
            setControls({ submit: false, reset: true });
        } catch (err) {
            showSubmitError(err);
        } finally {
            state.submitting = false;
        }
    }

    function applyGrades(result) {
        result.results.forEach((r) => {
            const box = byId("q" + r.n);
            if (!box) return;
            const fb = box.querySelector(".fb");
            box.classList.add(r.correct ? "correct" : "wrong");
            box.querySelectorAll("input").forEach((i) => (i.disabled = true));

            fb.textContent = "";
            const tag = d.createElement("span");
            tag.className = "tag";
            tag.textContent = r.correct
                ? "✓ Correct"
                : "✗ " + (r.given ? "You wrote: " + r.given : "No answer") + " · Answer: " + r.answer;
            fb.appendChild(tag);
            fb.appendChild(d.createElement("br"));

            const why = d.createElement("span");
            why.innerHTML = r.explanation || ""; // trusted, server-sanitized
            fb.appendChild(why);

            if (r.evidenceId) {
                fb.appendChild(d.createElement("br"));
                const btn = d.createElement("button");
                btn.type = "button";
                btn.className = "btn";
                btn.dataset.ev = r.evidenceId;
                btn.textContent = "Show in text";
                fb.appendChild(btn);
            }
            fb.hidden = false;
        });
        renderResult(result);
    }

    function renderResult(result) {
        if (!els.resultBox) return;
        const used = Math.max(0, result.elapsedSeconds || 0);
        const mm = Math.floor(used / 60);
        const ss = String(used % 60).padStart(2, "0");
        const pass = result.percent >= 80;
        const msg = pass ? "Great job! Next: Mini passage 5." : "Read the explanations, then tap Start again and try once more.";

        els.resultBox.className = "result";
        els.resultBox.hidden = false;
        els.resultBox.textContent = "";

        const label = d.createElement("p");
        label.className = "label";
        label.textContent = "Your result";
        const big = d.createElement("div");
        big.className = "big";
        big.textContent = result.score + "/" + result.total;
        const line = d.createElement("p");
        line.textContent = result.percent + "% · time " + mm + ":" + ss + ". " + msg;

        els.resultBox.appendChild(label);
        els.resultBox.appendChild(big);
        els.resultBox.appendChild(line);
        safeScroll(els.resultBox);
    }

    function showSubmitError(err) {
        if (!els.resultBox) return;
        els.resultBox.className = "result";
        els.resultBox.hidden = false;
        els.resultBox.textContent = "Couldn't submit your answers. Please try again.";
    }

    /* ---- restart ------------------------------------------------------ */
    function restart() {
        state.graded = false;
        state.submitting = false;
        if (els.form) els.form.reset();
        if (els.resultBox) { els.resultBox.hidden = true; els.resultBox.textContent = ""; els.resultBox.className = ""; }
        d.querySelectorAll(".q").forEach((q) => {
            q.classList.remove("correct", "wrong");
            const fb = q.querySelector(".fb");
            if (fb) { fb.hidden = true; fb.textContent = ""; }
            q.querySelectorAll("input").forEach((i) => (i.disabled = false));
        });
        d.querySelectorAll(".ev.on").forEach((x) => x.classList.remove("on"));
        if (els.passage) {
            els.passage.querySelectorAll("mark").forEach((m) => unwrap(m));
            els.passage.normalize();
        }
        resetTimer();
        setControls({ submit: true, reset: true });
        setInProgress(true);
        if (w.scrollTo) { try { w.scrollTo({ top: 0, behavior: "smooth" }); } catch (e) { /* noop */ } }
    }

    /* ---- controls + nav guard ---------------------------------------- */
    function setControls({ submit: canSubmit, reset: canReset }) {
        if (els.submitTop) els.submitTop.disabled = !canSubmit;
        if (els.formSubmitBtn) els.formSubmitBtn.disabled = !canSubmit;
        if (els.reset) els.reset.disabled = !canReset;
    }

    function setInProgress(value) {
        state.inProgress = value;
        const nav = w.BandUpNav;
        if (nav) {
            if (value && nav.registerGuard) nav.registerGuard(() => state.inProgress);
            else if (!value && nav.clearGuard) nav.clearGuard();
        }
    }

    function isInProgress() { return state.inProgress; }

    /* ---- event wiring ------------------------------------------------- */
    function wireEvents() {
        if (els.form) els.form.addEventListener("submit", submit);
        if (els.submitTop) els.submitTop.addEventListener("click", submit);
        if (els.reset) els.reset.addEventListener("click", restart);

        if (els.hlBtn) {
            els.hlBtn.addEventListener("mousedown", (e) => e.preventDefault());
            els.hlBtn.addEventListener("click", highlightSavedSelection);
        }

        d.addEventListener("mouseup", () => w.setTimeout && w.setTimeout(checkSelection, 10));
        d.addEventListener("touchend", () => w.setTimeout && w.setTimeout(checkSelection, 300));

        d.addEventListener("click", (e) => {
            const target = e.target;
            const closest = (sel) => (target.closest ? target.closest(sel) : null);

            const word = closest("#passage .w");
            if (word && !(selection() && selection().toString())) { showPopover(word); return; }

            if (!closest("#pop") && els.pop) els.pop.hidden = true;

            const mark = closest("#passage mark");
            if (mark && !(selection() && selection().toString())) { unwrap(mark); }

            const evBtn = closest("[data-ev]");
            if (evBtn) showEvidence(evBtn.dataset.ev);
        });
    }

    return {
        init,
        submit,
        restart,
        showEvidence,
        isInProgress,
        tick,
        // test seams:
        _state: state,
        _els: () => els,
        _applyHighlight: applyHighlight,
        _checkSelection: checkSelection,
        _stopTimer: stopTimer,
    };
}
