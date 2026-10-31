/* =========================================================================
   app.js — Reading Practice DOM controller (learner API, backend §7.4).

   createReadingApp({ api, doc, win, lang }) returns a controller with:
     init()            load the passage, render, start the timer, wire events
     setLanguage(code) reload only the help text (answers and timer stay)
     submit()          grade on the server and show the results
     restart()         a fresh attempt
     showEvidence(id)  highlight a passage evidence span
     isInProgress()    for the app-shell navigation guard

   HTML from the server (passage, instructions, explanations) is sanitized
   there and set with innerHTML; everything else uses textContent.
   ========================================================================= */

import { coverImage } from "../covers.js";
import { FAMILY, FIXED_CHOICES, formatClock, isLowTime, numberLabel, wordLimitHint } from "./core.js";

export function createReadingApp(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const api = options.api;

    const els = {};
    const state = {
        session: null,
        lang: options.lang || null,
        graded: false,
        submitting: false,
        inProgress: false,
        startedAt: 0,
        timer: { remaining: 0, threshold: 60, intervalId: null, running: false, enabled: false },
    };
    let savedRange = null;

    const byId = (id) => d.getElementById(id);
    const selection = () => (w.getSelection ? w.getSelection() : null);
    const safeScroll = (el) => {
        if (el && typeof el.scrollIntoView === "function") {
            try { el.scrollIntoView({ behavior: "smooth", block: "center" }); } catch (e) { /* noop */ }
        }
    };
    const el = (tag, className, text) => {
        const node = d.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    };

    function cacheEls() {
        els.root = byId("reading-root");
        els.stageLabel = byId("stageLabel");
        els.title = byId("passageTitle");
        els.timer = byId("timer");
        els.submitTop = byId("submit");
        els.passage = byId("passage");
        els.vocab = d.querySelector(".vocab");
        els.vocabTitle = byId("vocabTitle");
        els.vlist = byId("vlist");
        els.questions = byId("questions");
        els.form = byId("form");
        els.resultBox = byId("resultBox");
        els.reset = byId("reset");
        els.hlBtn = byId("hlBtn");
        els.pop = byId("pop");
        els.helpLang = byId("helpLang");
        els.formSubmitBtn = els.form ? els.form.querySelector('button[type="submit"]') : null;
    }

    /* ---- lifecycle ---------------------------------------------------- */
    async function init() {
        cacheEls();
        if (!els.root) return;
        wireEvents();
        showLoading();
        try {
            const session = await api.fetchSession(els.root.dataset.sessionId, state.lang);
            state.session = session;
            renderSession(session);
            if (await openReview()) return;
            startTimer(session.durationSeconds, session.lowTimeThresholdSeconds);
            setControls({ submit: true, reset: true });
            setInProgress(true);
        } catch (err) {
            showError(err);
        }
    }

    /* ---- review mode (/reading/{slug}?review={attemptId}, "My progress") --- */
    function reviewId() {
        try {
            return new URLSearchParams(w.location.search).get("review");
        } catch (e) {
            return null;
        }
    }

    /** Shows a stored attempt: its answers filled in and graded, no timer. False to practise normally. */
    async function openReview() {
        const id = reviewId();
        if (!id || !api.fetchReview) return false;
        let review;
        try {
            review = await api.fetchReview(id, state.lang);
        } catch (e) {
            return false; // not theirs, gone, or signed out: just practise
        }
        if (review.passageId !== state.session.id) return false;
        fillAnswers(review.result.results);
        state.graded = true;
        applyGrades(review.result);
        if (els.timer) els.timer.textContent = "--:--";
        setControls({ submit: false, reset: true });
        setInProgress(false);
        const when = new Date(review.submittedAt);
        const note = el("p", "review-note",
            "Reviewing your attempt from " + when.toLocaleDateString() + " " + when.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
            + (review.updatedSince ? ". This passage was updated since, so some questions may read differently." : ".")
            + " Press \"Start again\" to practise it anew.");
        els.resultBox.insertBefore(note, els.resultBox.firstChild);
        return true;
    }

    /** Puts stored answers back into the form (the same controls collectAnswers reads). */
    function fillAnswers(results) {
        results.forEach((r) => {
            const given = r.given;
            if (Array.isArray(given)) {
                d.querySelectorAll('input[name="q' + r.n + '"]').forEach((i) => (i.checked = given.includes(i.value)));
                return;
            }
            if (!given) return;
            const radio = [...d.querySelectorAll('input[name="q' + r.n + '"]')].find((i) => i.value === given);
            if (radio) { radio.checked = true; return; }
            const select = byId("sel" + r.n);
            if (select) { select.value = given; return; }
            const input = byId("in" + r.n);
            if (input) input.value = given;
        });
        d.querySelectorAll(".qgroup").forEach(markUsedOptions);
    }

    function showLoading() {
        if (els.passage) { els.passage.classList.add("panel__status"); els.passage.textContent = "Loading passage…"; }
        if (els.questions) { els.questions.classList.add("panel__status"); els.questions.textContent = "Loading questions…"; }
        setControls({ submit: false, reset: false });
    }

    function showError(err) {
        const missing = err && err.status === 404;
        if (els.stageLabel) els.stageLabel.textContent = "";
        if (els.title) els.title.textContent = missing ? "Passage not available" : "";
        if (els.passage) {
            els.passage.classList.add("panel__status");
            els.passage.textContent = missing
                ? "This passage isn't available. It may have been removed or not published yet. "
                : "Couldn't load this passage. Check your connection and try again. ";
            if (!missing) {
                const retry = el("button", "btn", "Retry");
                retry.type = "button";
                retry.id = "retry";
                retry.addEventListener("click", () => init());
                els.passage.appendChild(retry);
            } else {
                const home = el("a", "btn", "Back to Orbiko");
                home.href = "/";
                els.passage.appendChild(home);
            }
        }
        if (els.questions) { els.questions.classList.add("panel__status"); els.questions.textContent = ""; }
        if (els.timer) els.timer.textContent = "--:--";
        setControls({ submit: false, reset: false });
    }

    /* ---- rendering ---------------------------------------------------- */
    function renderSession(session) {
        renderHeader(session);
        renderPassage(session);
        renderHelp(session);
        renderQuestions(session);
    }

    function renderHeader(session) {
        if (els.stageLabel) {
            const parts = [];
            if (session.stageLabel) parts.push(session.stageLabel);
            if (session.position) parts.push("Passage " + session.position);
            els.stageLabel.textContent = parts.join(" · ");
        }
        if (els.title) els.title.textContent = session.title || "";
        const cover = byId("passageCover");
        if (cover && !cover.firstChild) {
            cover.appendChild(coverImage(session.coverUrl, session.topics));
            cover.hidden = false;
        }
    }

    function renderPassage(session) {
        els.passage.classList.remove("panel__status");
        els.passage.innerHTML = session.passageHtml || ""; // server-sanitized
        ensureTips();
    }

    function ensureTips() {
        const panel = els.passage ? els.passage.parentNode : null;
        if (!panel || panel.querySelector(".tips")) return;
        const tips = el("p", "tips", "Select text to highlight it. Tap a highlight to remove it. Tap a dotted word for its meaning.");
        panel.insertBefore(tips, els.passage);
    }

    /** Glosses, translated instructions and the language picker: everything that follows the help language. */
    function renderHelp(session) {
        const glosses = session.glosses || {};
        const dir = session.direction === "rtl" ? "rtl" : "ltr";
        els.passage.querySelectorAll(".w").forEach((word) => {
            word.classList.toggle("nogloss", !glosses[word.dataset.g]);
        });
        if (els.vlist) {
            els.vlist.textContent = "";
            els.vlist.dir = dir;
            const seen = new Set();
            els.passage.querySelectorAll(".w").forEach((word) => {
                const meaning = glosses[word.dataset.g];
                if (!meaning || seen.has(word.dataset.g)) return;
                seen.add(word.dataset.g);
                const row = el("div");
                row.appendChild(el("span", null, word.textContent));
                row.appendChild(el("span", null, meaning));
                els.vlist.appendChild(row);
            });
            if (els.vocab) els.vocab.hidden = seen.size === 0;
        }
        if (els.vocabTitle) {
            const option = els.helpLang && [...els.helpLang.options].find((o) => o.value === session.language);
            els.vocabTitle.textContent = "Word list" + (option ? " (" + option.textContent + ")" : "");
        }
        if (els.pop) els.pop.dir = dir;
        if (els.helpLang && session.language) els.helpLang.value = session.language;
        d.querySelectorAll("[data-instruction-translated]").forEach((p) => {
            const group = (session.questionGroups || [])[Number(p.dataset.instructionTranslated)];
            p.innerHTML = (group && group.instructionTranslated) || ""; // server-sanitized
            p.hidden = !p.innerHTML;
            p.dir = dir;
        });
    }

    function renderQuestions(session) {
        els.questions.classList.remove("panel__status");
        els.questions.textContent = "";
        (session.questionGroups || []).forEach((group, index) => {
            const groupEl = el("div", "qgroup");
            groupEl.dataset.group = String(index);
            groupEl.appendChild(el("h3", null, group.heading || headingOf(group)));

            const instr = el("p", "instr");
            instr.innerHTML = group.instruction || ""; // server-sanitized
            groupEl.appendChild(instr);
            const translated = el("p", "instr instr--help");
            translated.dataset.instructionTranslated = String(index);
            translated.hidden = true;
            groupEl.appendChild(translated);

            const hint = wordLimitHint(group.maxWords, group.numbersAllowed);
            // Shown only when the instruction doesn't already say it (IELTS instructions usually do).
            if (hint && !instr.textContent.toUpperCase().includes(hint)) groupEl.appendChild(el("p", "limit", hint));

            if (FAMILY[group.type] === "match" && (group.options || []).length) {
                groupEl.appendChild(renderBank(group));
            }
            (group.questions || []).forEach((q) => groupEl.appendChild(renderQuestion(q, group)));
            els.questions.appendChild(groupEl);
        });
        renderHelp(session);
    }

    function headingOf(group) {
        const qs = group.questions || [];
        if (!qs.length) return "";
        const first = qs[0].n;
        const last = qs[qs.length - 1].nTo || qs[qs.length - 1].n;
        return first === last ? "Question " + first : "Questions " + first + "–" + last;
    }

    /** The matching group's options, listed once above its questions. */
    function renderBank(group) {
        const list = el("dl", "bank");
        group.options.forEach((o) => {
            list.appendChild(el("dt", null, o.key));
            list.appendChild(el("dd", null, o.label));
        });
        return list;
    }

    function renderQuestion(q, group) {
        const family = FAMILY[q.type] || "gap";
        const box = el("div", "q");
        box.id = "q" + q.n;
        box.dataset.family = family;
        const stem = el("div", "stem");
        stem.appendChild(el("span", "num" + (q.nTo ? " num--range" : ""), numberLabel(q)));
        box.appendChild(stem);

        if (family === "fixed" || family === "single" || family === "multi") {
            stem.appendChild(el("span", null, q.text || ""));
            box.appendChild(renderChoices(q, group, family));
        } else if (family === "match") {
            const select = renderSelect(q, group);
            if (q.pre != null) {
                stem.appendChild(gapLine(q, select));
            } else {
                stem.appendChild(el("span", null, q.text || ""));
                const line = el("div", "match");
                line.appendChild(select);
                box.appendChild(line);
            }
        } else {
            const input = el("input", "fill");
            input.name = "q" + q.n;
            input.id = "in" + q.n;
            input.setAttribute("aria-label", "Answer " + q.n);
            input.setAttribute("autocapitalize", "off");
            input.spellcheck = false;
            if (q.pre != null) {
                stem.appendChild(gapLine(q, input));
            } else {
                stem.appendChild(el("span", null, q.text || ""));
                const line = el("div", "match");
                line.appendChild(input);
                box.appendChild(line);
            }
        }
        const fb = el("div", "fb");
        fb.hidden = true;
        box.appendChild(fb);
        return box;
    }

    function gapLine(q, control) {
        const text = el("span");
        text.appendChild(d.createTextNode((q.pre || "") + " "));
        text.appendChild(control);
        text.appendChild(d.createTextNode(" " + (q.post || "")));
        return text;
    }

    function renderChoices(q, group, family) {
        const opts = el("div", "opts" + (family === "fixed" ? "" : " opts--list"));
        opts.setAttribute("role", family === "multi" ? "group" : "radiogroup");
        opts.setAttribute("aria-label", "Question " + numberLabel(q));
        const choices = family === "fixed"
            ? (FIXED_CHOICES[q.type] || []).map((v) => ({ key: v, label: v }))
            : (q.options || []);
        choices.forEach((choice, i) => {
            const label = el("label");
            const input = el("input");
            input.type = family === "multi" ? "checkbox" : "radio";
            input.name = "q" + q.n;
            input.value = choice.key;
            input.id = "q" + q.n + "o" + i;
            label.appendChild(input);
            if (family === "fixed") {
                label.appendChild(d.createTextNode(choice.label));
            } else {
                label.appendChild(el("b", "key", choice.key));
                label.appendChild(d.createTextNode(" " + choice.label));
            }
            opts.appendChild(label);
        });
        if (family === "multi") {
            const limit = Number(group.chooseCount) || choices.length;
            opts.dataset.choose = String(limit);
            opts.appendChild(el("p", "limit", "Choose " + (WORD[limit] || limit) + "."));
        }
        return opts;
    }

    const WORD = ["", "ONE", "TWO", "THREE", "FOUR", "FIVE"];

    function renderSelect(q, group) {
        const select = el("select", "pick");
        select.id = "sel" + q.n;
        select.name = "q" + q.n;
        select.setAttribute("aria-label", "Answer " + q.n);
        select.dataset.reusable = group.optionsReusable ? "true" : "false";
        select.appendChild(el("option", null, "—")).value = "";
        (group.options || []).forEach((o) => {
            const option = el("option", null, o.key);
            option.value = o.key;
            option.dataset.key = o.key;
            select.appendChild(option);
        });
        return select;
    }

    /** "Choose TWO": once two are ticked, the others can't be. */
    function enforceChooseLimit(opts) {
        const limit = Number(opts.dataset.choose);
        const boxes = [...opts.querySelectorAll("input[type=checkbox]")];
        const ticked = boxes.filter((b) => b.checked).length;
        boxes.forEach((b) => { if (!state.graded) b.disabled = !b.checked && ticked >= limit; });
    }

    /** Matching without reuse: an option picked for one question shows as used in the others. */
    function markUsedOptions(groupEl) {
        const selects = [...groupEl.querySelectorAll("select.pick")];
        if (!selects.length || selects[0].dataset.reusable === "true") return;
        selects.forEach((select) => {
            const usedElsewhere = new Set(selects.filter((s) => s !== select && s.value).map((s) => s.value));
            [...select.options].forEach((option) => {
                if (!option.dataset.key) return;
                option.textContent = option.dataset.key + (usedElsewhere.has(option.value) ? " (used)" : "");
            });
        });
    }

    /* ---- help language ------------------------------------------------ */
    async function setLanguage(code) {
        state.lang = code;
        if (!state.session) return;
        try {
            const help = await api.fetchSession(state.session.id, code);
            state.session.language = help.language;
            state.session.direction = help.direction;
            state.session.glosses = help.glosses;
            state.session.stageLabel = help.stageLabel;
            (state.session.questionGroups || []).forEach((g, i) => {
                const fresh = (help.questionGroups || [])[i];
                g.instructionTranslated = fresh ? fresh.instructionTranslated : undefined;
            });
            renderHeader(state.session);
            renderHelp(state.session);
        } catch (e) {
            /* keep the current help text */
        }
    }

    /* ---- timer -------------------------------------------------------- */
    function startTimer(duration, threshold) {
        state.startedAt = Date.now();
        clearTimerInterval();
        state.timer.enabled = Number(duration) > 0;
        if (els.timer) els.timer.hidden = !state.timer.enabled;
        if (!state.timer.enabled) return;
        state.timer.remaining = duration;
        state.timer.threshold = threshold == null ? -Infinity : threshold;
        renderTimer();
        state.timer.running = true;
        if (w.setInterval) state.timer.intervalId = w.setInterval(() => tick(), 1000);
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

    /* ---- gloss popover ------------------------------------------------ */
    function showPopover(wordEl) {
        const meaning = state.session && (state.session.glosses || {})[wordEl.dataset.g];
        if (!els.pop || !meaning) return;
        els.pop.textContent = "";
        els.pop.appendChild(el("b", null, wordEl.textContent));
        els.pop.appendChild(d.createTextNode(meaning));
        place(els.pop, wordEl);
    }
    function place(node, anchor) {
        node.hidden = false;
        if (!anchor.getBoundingClientRect) return;
        const r = anchor.getBoundingClientRect();
        const width = node.offsetWidth || 0;
        const viewport = (d.documentElement && d.documentElement.clientWidth) || 0;
        const max = Math.max(8, viewport - width - 8);
        const x = r.left + r.width / 2 - width / 2;
        node.style.left = Math.max(8, Math.min(x, max)) + "px";
        node.style.top = (r.bottom + (w.scrollY || 0) + 8) + "px";
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
        const target = els.passage ? els.passage.querySelector("#" + CSS.escape(evidenceId)) : null;
        if (target) { target.classList.add("on"); safeScroll(target); }
    }

    /* ---- submit + results --------------------------------------------- */
    /** One answer per question, as the API expects: text, or a list of letters for "Choose N". */
    function collectAnswers() {
        const out = [];
        (state.session.questionGroups || []).forEach((g) =>
            (g.questions || []).forEach((q) => {
                const family = FAMILY[q.type] || "gap";
                let value;
                if (family === "multi") {
                    value = [...d.querySelectorAll('input[name="q' + q.n + '"]:checked')].map((i) => i.value);
                } else if (family === "fixed" || family === "single") {
                    const checked = d.querySelector('input[name="q' + q.n + '"]:checked');
                    value = checked ? checked.value : "";
                } else if (family === "match") {
                    const select = byId("sel" + q.n);
                    value = select ? select.value : "";
                } else {
                    const input = byId("in" + q.n);
                    value = input ? input.value.trim() : "";
                }
                out.push({ n: q.n, value });
            })
        );
        return out;
    }

    function elapsedSeconds() {
        return Math.max(0, Math.round((Date.now() - state.startedAt) / 1000));
    }

    async function submit(e) {
        if (e && e.preventDefault) e.preventDefault();
        if (state.submitting || state.graded || !state.session) return;
        state.submitting = true;
        setControls({ submit: false, reset: false });
        try {
            const result = await api.submitAttempt({
                passageId: state.session.id,
                elapsedSeconds: elapsedSeconds(),
                answers: collectAnswers(),
            }, state.lang);
            state.graded = true;
            applyGrades(result);
            stopTimer();
            setInProgress(false);
            setControls({ submit: false, reset: true });
        } catch (err) {
            showSubmitError(err);
            setControls({ submit: true, reset: true });
        } finally {
            state.submitting = false;
        }
    }

    function shown(given) {
        if (Array.isArray(given)) return given.join(", ");
        return given || "";
    }

    function applyGrades(result) {
        const dir = state.session.direction === "rtl" ? "rtl" : "ltr";
        result.results.forEach((r) => {
            const box = byId("q" + r.n);
            if (!box) return;
            const fb = box.querySelector(".fb");
            const partial = !r.correct && r.points > 0;
            box.classList.add(r.correct ? "correct" : partial ? "partial" : "wrong");
            box.querySelectorAll("input, select").forEach((i) => (i.disabled = true));

            fb.textContent = "";
            const given = shown(r.given);
            let line;
            if (r.correct) {
                line = "✓ Correct" + (r.possible > 1 ? " (" + r.points + "/" + r.possible + ")" : "");
            } else {
                line = (partial ? "◐ Partly right (" + r.points + "/" + r.possible + ")" : "✗")
                        + " · " + (given ? "You answered: " + given : "No answer") + " · Answer: " + r.answer;
            }
            fb.appendChild(el("span", "tag", line));
            const others = (r.acceptedAnswers || []).filter((a) => a !== r.answer);
            if (others.length && box.dataset.family === "gap") {
                fb.appendChild(el("span", "also", " Also accepted: " + others.join(", ")));
            }
            if (r.explanation) {
                const why = el("div", "why");
                why.dir = dir;
                why.innerHTML = r.explanation; // server-sanitized
                fb.appendChild(why);
            }
            if (r.evidenceId) {
                const btn = el("button", "btn", "Show in text");
                btn.type = "button";
                btn.dataset.ev = r.evidenceId;
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
        const msg = result.percent >= 80
            ? "Great job!"
            : "Read the explanations, then tap Start again and try once more.";
        els.resultBox.className = "result";
        els.resultBox.hidden = false;
        els.resultBox.textContent = "";
        els.resultBox.appendChild(el("p", "label", "Your result"));
        els.resultBox.appendChild(el("div", "big", result.score + "/" + result.total));
        els.resultBox.appendChild(el("p", null, result.percent + "% · time " + mm + ":" + ss + ". " + msg));
        safeScroll(els.resultBox);
    }

    function showSubmitError(err) {
        if (!els.resultBox) return;
        els.resultBox.className = "result result--error";
        els.resultBox.hidden = false;
        els.resultBox.textContent = err && err.status === 400
            ? "Something went wrong with your answers. Please check them and submit again."
            : err && err.status === 404
                ? "This passage is no longer available."
                : "Couldn't submit your answers. Check your connection and try again — your answers are kept.";
    }

    /* ---- restart ------------------------------------------------------ */
    function restart() {
        state.graded = false;
        state.submitting = false;
        // Not els.form.reset(): the "Start again" button has id="reset", which shadows the method.
        if (els.form) HTMLFormElement.prototype.reset.call(els.form);
        if (els.resultBox) { els.resultBox.hidden = true; els.resultBox.textContent = ""; els.resultBox.className = ""; }
        d.querySelectorAll(".q").forEach((q) => {
            q.classList.remove("correct", "wrong", "partial");
            const fb = q.querySelector(".fb");
            if (fb) { fb.hidden = true; fb.textContent = ""; }
            q.querySelectorAll("input, select").forEach((i) => (i.disabled = false));
        });
        d.querySelectorAll(".qgroup").forEach(markUsedOptions);
        d.querySelectorAll(".ev.on").forEach((x) => x.classList.remove("on"));
        if (els.passage) {
            els.passage.querySelectorAll("mark").forEach((m) => unwrap(m));
            els.passage.normalize();
        }
        stopTimer();
        startTimer(state.session.durationSeconds, state.session.lowTimeThresholdSeconds);
        if (reviewId() && w.history && w.history.replaceState) {
            try { w.history.replaceState(null, "", w.location.pathname); } catch (e) { /* noop */ }
        }
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
        if (els.wired) return;
        els.wired = true;
        if (els.form) els.form.addEventListener("submit", submit);
        if (els.submitTop) els.submitTop.addEventListener("click", submit);
        if (els.reset) els.reset.addEventListener("click", restart);
        if (els.helpLang) els.helpLang.addEventListener("change", () => {
            if (options.onLanguage) options.onLanguage(els.helpLang.value);
            setLanguage(els.helpLang.value);
        });

        if (els.hlBtn) {
            els.hlBtn.addEventListener("mousedown", (e) => e.preventDefault());
            els.hlBtn.addEventListener("click", highlightSavedSelection);
        }
        d.addEventListener("mouseup", () => w.setTimeout && w.setTimeout(checkSelection, 10));
        d.addEventListener("touchend", () => w.setTimeout && w.setTimeout(checkSelection, 300));

        d.addEventListener("change", (e) => {
            const opts = e.target.closest && e.target.closest(".opts[data-choose]");
            if (opts) enforceChooseLimit(opts);
            const group = e.target.matches && e.target.matches("select.pick") ? e.target.closest(".qgroup") : null;
            if (group) markUsedOptions(group);
        });

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
        setLanguage,
        submit,
        restart,
        showEvidence,
        isInProgress,
        tick,
        _state: state,
    };
}
