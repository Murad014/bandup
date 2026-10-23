/* =========================================================================
   editor.js — the exercise editor on the admin API (backend design §8.4).

   The model is the AdminExerciseView the API returns: groups and questions
   keep their ids (new ones have none), each question holds its own answer
   (always a list) and explanations per language, glosses are data-g keys
   with meanings per language. Saving sends ExerciseInput with the loaded
   version; read-only fields (n, heading, surface, status, revision) are
   never sent.

   The server checks everything (422 field errors, 409 stale version);
   validate.js only shows the same publish rules as hints while writing.
   ========================================================================= */

import { createPassageEditor } from "./passage.js";
import { publishHints, questionLabels } from "./validate.js";
import { HELP_LANGUAGES, QUESTION_TYPES, familyOf, optionKeyAt, orderedTypeKeys, typeDef,
    usesGroupOptions, usesPerQuestionOptions } from "./questionTypes.js";

const LEVELS = ["", "A1", "A2", "B1", "B2", "C1", "C2"];
const STASH = "bandup.unsaved.";

function blankExercise() {
    return {
        id: "", version: 0, status: "DRAFT", revision: 1, skill: "READING", targetLanguage: "en",
        title: "", format: "MINI", level: null, durationSeconds: 300, lowTimeThresholdSeconds: 60,
        stageId: null, position: null, topicIds: [], passageHtml: "", glosses: {}, questionGroups: [],
    };
}

const clone = (v) => JSON.parse(JSON.stringify(v));
const numberOrNull = (v) => (v === "" || v == null || Number.isNaN(Number(v)) ? null : Number(v));

export function createEditor(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const api = options.api;

    const els = {};
    const state = { slug: null, ex: blankExercise(), dirty: false, passage: null, busy: false };
    const byId = (id) => d.getElementById(id);

    /* ---- new pieces ---------------------------------------------------- */
    function newQuestion(type) {
        const family = familyOf(type);
        const q = { id: null, evidenceId: "", answer: [], explanations: {}, options: [] };
        if (typeDef(type).stem === "gap") { q.pre = ""; q.post = ""; } else { q.text = ""; }
        if (family === "mcSingle") q.options = seedOptions(3);
        if (family === "mcMulti") q.options = seedOptions(5);
        return q;
    }
    function seedOptions(n, roman) {
        return Array.from({ length: n }, (_, i) => ({ key: optionKeyAt(i, roman), label: "" }));
    }
    function newGroup(type) {
        const g = { id: null, type, instruction: "", instructionTranslations: {}, chooseCount: null, maxWords: null,
            numbersAllowed: false, optionsReusable: false, mediaId: null, options: [], questions: [newQuestion(type)] };
        if (usesGroupOptions(type)) g.options = seedOptions(4, type === "MATCH_HEADINGS");
        if (familyOf(type) === "mcMulti") g.chooseCount = 2;
        return g;
    }

    /* ---- lifecycle ---------------------------------------------------- */
    async function init() {
        els.root = byId("editorRoot");
        if (!els.root) return;
        ["slugField", "slug", "title", "format", "level", "durationSeconds", "lowTimeThresholdSeconds", "stageId",
            "position", "topicIds", "groups", "glosses", "editorErrors", "editorNotice", "publishHints", "statusBadge",
            "revisionInfo", "dirtyDot", "evidenceList", "saveBtn", "publishBtn", "unpublishBtn"]
            .forEach((id) => (els[id] = byId(id)));
        LEVELS.forEach((l) => els.level.appendChild(option(l, l || "—")));

        state.passage = createPassageEditor({
            doc: d, win: w, editEl: byId("passageEdit"), previewEl: byId("passagePreview"),
            onChange: (html) => { state.ex.passageHtml = html; renderGlosses(); refreshEvidenceList(); changed(); },
        });
        wireStaticEvents();

        state.slug = els.root.dataset.sessionId || null;
        if (state.slug) {
            try {
                load(await api.get(state.slug));
            } catch (e) {
                if (handleSignedOut(e)) return;
                showErrors([{ field: "", message: e.status === 404 ? "This exercise doesn't exist." : "Couldn't load this exercise." }]);
                return;
            }
        } else {
            load(blankExercise());
        }
        offerStash();
        const nav = w.BandUpAdminNav;
        if (nav && nav.registerGuard) nav.registerGuard(() => state.dirty);
    }

    function load(view) {
        state.ex = clone(view);
        state.ex.questionGroups.forEach((g) => g.questions.forEach((q) => {
            q.answer = q.answer || [];
            q.explanations = q.explanations || {};
            q.options = q.options || [];
        }));
        populateMetadata();
        state.passage.setHtml(state.ex.passageHtml || "");
        renderGlosses();
        refreshEvidenceList();
        renderGroups();
        renderStatus();
        clearDirty();
        renderHints();
    }

    /* ---- model → ExerciseInput --------------------------------------- */
    function toInput() {
        const ex = state.ex;
        const present = new Set(state.passage.glossMarks().map((m) => m.key));
        const glosses = {};
        Object.entries(ex.glosses || {}).forEach(([key, g]) => {
            if (present.has(key)) glosses[key] = { lemma: g.lemma || null, meanings: g.meanings || {} };
        });
        return {
            id: state.slug ? undefined : (ex.id || "").trim(),
            version: state.slug ? ex.version : undefined,
            skill: ex.skill, targetLanguage: ex.targetLanguage,
            title: ex.title, format: ex.format, level: ex.level || null,
            durationSeconds: numberOrNull(ex.durationSeconds), lowTimeThresholdSeconds: numberOrNull(ex.lowTimeThresholdSeconds),
            stageId: numberOrNull(ex.stageId), position: numberOrNull(ex.position),
            topicIds: ex.topicIds || [],
            passageHtml: ex.passageHtml || "",
            glosses,
            questionGroups: ex.questionGroups.map((g) => {
                const family = familyOf(g.type);
                return {
                    id: g.id || null, type: g.type, instruction: g.instruction || "",
                    instructionTranslations: g.instructionTranslations || {},
                    chooseCount: family === "mcMulti" ? numberOrNull(g.chooseCount) : null,
                    maxWords: family === "gap" ? numberOrNull(g.maxWords) : null,
                    numbersAllowed: family === "gap" ? !!g.numbersAllowed : false,
                    optionsReusable: family === "match" ? !!g.optionsReusable : false,
                    mediaId: g.mediaId || null,
                    options: usesGroupOptions(g.type) ? g.options.map((o) => ({ key: o.key, label: o.label || "" })) : [],
                    questions: g.questions.map((q) => {
                        const gap = typeDef(g.type).stem === "gap";
                        return {
                            id: q.id || null,
                            text: gap ? null : (q.text || ""),
                            pre: gap ? (q.pre || "") : null,
                            post: gap ? (q.post || "") : null,
                            evidenceId: (q.evidenceId || "").trim() || null,
                            options: usesPerQuestionOptions(g.type) ? q.options.map((o) => ({ key: o.key, label: o.label || "" })) : [],
                            answer: (q.answer || []).map((a) => String(a).trim()).filter((a) => a !== ""),
                            explanations: q.explanations || {},
                        };
                    }),
                };
            }),
        };
    }

    /* ---- metadata ---------------------------------------------------- */
    function populateMetadata() {
        const ex = state.ex;
        els.slugField.hidden = !!state.slug;
        els.slug.value = ex.id || "";
        els.title.value = ex.title || "";
        els.format.value = ex.format || "MINI";
        els.level.value = ex.level || "";
        els.durationSeconds.value = ex.durationSeconds == null ? "" : ex.durationSeconds;
        els.lowTimeThresholdSeconds.value = ex.lowTimeThresholdSeconds == null ? "" : ex.lowTimeThresholdSeconds;
        els.stageId.value = ex.stageId == null ? "" : ex.stageId;
        els.position.value = ex.position == null ? "" : ex.position;
        els.topicIds.value = (ex.topicIds || []).join(", ");
    }

    function renderStatus() {
        const status = state.ex.status || "DRAFT";
        els.statusBadge.textContent = status.toLowerCase();
        els.statusBadge.className = "badge badge--" + status.toLowerCase();
        els.revisionInfo.textContent = state.slug ? "Revision " + state.ex.revision + " · version " + state.ex.version : "New exercise";
        els.publishBtn.hidden = status === "PUBLISHED" || status === "ARCHIVED";
        els.unpublishBtn.hidden = status !== "PUBLISHED";
        els.saveBtn.textContent = status === "PUBLISHED" ? "Save changes" : "Save draft";
        const archived = status === "ARCHIVED";
        els.root.classList.toggle("is-archived", archived);
        els.root.querySelectorAll("input, select, textarea, button, [contenteditable]").forEach((c) => {
            if (c.id === "editorReload") return;
            if (c.isContentEditable || c.hasAttribute("contenteditable")) c.contentEditable = archived ? "false" : "true";
            else c.disabled = archived;
        });
        if (archived) notice("This exercise is archived: learners have attempts on it, so it's kept for their history and can't be edited.");
    }

    /* ---- glosses ------------------------------------------------------ */
    function renderGlosses() {
        if (!els.glosses) return;
        const marks = state.passage.glossMarks();
        els.glosses.textContent = "";
        if (!marks.length) {
            els.glosses.appendChild(text("p", "admin-sub", "Select a word in the passage and press \"Add gloss\" to give it a meaning."));
            return;
        }
        const seen = new Set();
        marks.forEach((mark) => {
            if (seen.has(mark.key)) return;
            seen.add(mark.key);
            const gloss = state.ex.glosses[mark.key] || (state.ex.glosses[mark.key] = { lemma: null, meanings: {} });
            gloss.surface = mark.surface;
            const row = div("gloss-row");
            row.dataset.field = "glosses." + mark.key;
            const head = div("gloss-head");
            head.appendChild(text("span", "opt-key", mark.key));
            head.appendChild(text("b", null, mark.surface));
            row.appendChild(head);
            row.appendChild(field("Dictionary form (optional)", textInput(gloss.lemma, (v) => { gloss.lemma = v || null; changed(); })));
            HELP_LANGUAGES.forEach((lang) => {
                const meaning = gloss.meanings[lang.code];
                const input = textInput(meaning ? meaning.text : "", (v) => {
                    if (v.trim()) gloss.meanings[lang.code] = { text: v, source: "HUMAN", reviewed: true };
                    else delete gloss.meanings[lang.code];
                    changed();
                });
                const f = field("Meaning — " + lang.name, input, "glosses." + mark.key + ".meanings." + lang.code);
                if (meaning && meaning.source === "MACHINE" && !meaning.reviewed) {
                    f.appendChild(text("span", "machine", "Machine-written, not reviewed yet — editing it makes it reviewed."));
                }
                row.appendChild(f);
            });
            els.glosses.appendChild(row);
        });
    }

    function refreshEvidenceList() {
        if (!els.evidenceList) return;
        els.evidenceList.textContent = "";
        state.passage.evidenceIds().forEach((id) => els.evidenceList.appendChild(option(id, id)));
    }

    /* ---- question groups --------------------------------------------- */
    function renderGroups() {
        els.groups.textContent = "";
        const labels = questionLabels(state.ex);
        state.ex.questionGroups.forEach((g, gi) => els.groups.appendChild(groupCard(g, gi, labels)));
        const addRow = div("admin-row");
        addRow.appendChild(button("+ Add question group", () => {
            state.ex.questionGroups.push(newGroup("TFNG"));
            changed();
            renderGroups();
        }, "btn btn--primary"));
        els.groups.appendChild(addRow);
    }

    function groupCard(g, gi, labels) {
        const path = "questionGroups[" + gi + "]";
        const family = familyOf(g.type);
        const card = div("card group-card");
        card.dataset.field = path;

        const head = div("admin-row");
        const first = g.questions.length ? labels.get(g.questions[0]) : "";
        const last = g.questions.length ? labels.get(g.questions[g.questions.length - 1]) : "";
        head.appendChild(text("h3", null, g.questions.length ? (first === last ? first : first + " – " + last.replace("Q", "")) : "Empty group"));
        head.appendChild(div("admin-spacer"));
        head.appendChild(button("↑", () => move(state.ex.questionGroups, gi, -1), "btn btn--text", "Move group up"));
        head.appendChild(button("↓", () => move(state.ex.questionGroups, gi, 1), "btn btn--text", "Move group down"));
        head.appendChild(button("Remove group", () => { state.ex.questionGroups.splice(gi, 1); changed(); renderGroups(); }, "btn btn--danger"));
        card.appendChild(head);

        const typeSel = d.createElement("select");
        orderedTypeKeys().forEach((k) => {
            const o = option(k, QUESTION_TYPES[k].label);
            o.disabled = !!QUESTION_TYPES[k].disabled && k !== g.type;
            o.selected = k === g.type;
            typeSel.appendChild(o);
        });
        typeSel.addEventListener("change", () => changeGroupType(gi, typeSel.value));
        card.appendChild(field("Question type", typeSel, path + ".type"));

        card.appendChild(field("Instruction (official, HTML)", textInput(g.instruction, (v) => { g.instruction = v; changed(); }), path + ".instruction"));
        HELP_LANGUAGES.forEach((lang) => {
            card.appendChild(field("Instruction — " + lang.name + " help (optional)",
                textInput((g.instructionTranslations || {})[lang.code], (v) => {
                    g.instructionTranslations = g.instructionTranslations || {};
                    if (v.trim()) g.instructionTranslations[lang.code] = v; else delete g.instructionTranslations[lang.code];
                    changed();
                }), path + ".instructionTranslations." + lang.code));
        });

        if (family === "mcMulti") {
            card.appendChild(field("How many answers to choose", numberInput(g.chooseCount, (v) => {
                g.chooseCount = numberOrNull(v);
                changed();
                renderHints();
            }), path + ".chooseCount"));
        }
        if (family === "gap") {
            const limit = div("grid-2");
            limit.appendChild(field("Word limit (empty = none)", numberInput(g.maxWords, (v) => { g.maxWords = numberOrNull(v); changed(); }), path + ".maxWords"));
            limit.appendChild(checkField("Numbers don't count (\"AND/OR A NUMBER\")", g.numbersAllowed, (v) => { g.numbersAllowed = v; changed(); }));
            card.appendChild(limit);
        }
        if (usesGroupOptions(g.type)) {
            card.appendChild(checkField("Options may be used more than once", g.optionsReusable, (v) => { g.optionsReusable = v; changed(); }));
            card.appendChild(optionBank(typeDef(g.type).optionsLabel || "Options", g.options, path + ".options", g.type === "MATCH_HEADINGS"));
        }
        if (g.type === "DIAGRAM_LABEL") {
            card.appendChild(text("p", "admin-error", "Diagram questions need a picture; pictures come with the media feature, so this group can't be published yet."));
        }

        g.questions.forEach((q, qi) => card.appendChild(questionCard(g, gi, q, qi, labels.get(q))));
        const row = div("admin-row");
        row.appendChild(button("+ Add question", () => { g.questions.push(newQuestion(g.type)); changed(); renderGroups(); }));
        card.appendChild(row);
        return card;
    }

    function changeGroupType(gi, type) {
        const old = state.ex.questionGroups[gi];
        const fresh = newGroup(type);
        fresh.id = old.id;
        fresh.instruction = old.instruction;
        fresh.instructionTranslations = old.instructionTranslations;
        fresh.questions = old.questions.map((q) => {
            const nq = newQuestion(type);
            nq.id = q.id;
            nq.evidenceId = q.evidenceId;
            nq.explanations = q.explanations;
            if (nq.text !== undefined) nq.text = q.text != null ? q.text : [q.pre, q.post].filter(Boolean).join(" ___ ");
            if (nq.pre !== undefined) nq.pre = q.pre != null ? q.pre : (q.text || "");
            return nq;
        });
        state.ex.questionGroups[gi] = fresh;
        changed();
        renderGroups();
    }

    function questionCard(g, gi, q, qi, label) {
        const path = "questionGroups[" + gi + "].questions[" + qi + "]";
        const family = familyOf(g.type);
        const box = div("q-card");
        box.dataset.field = path;

        const head = div("q-head");
        head.appendChild(text("span", "q-num", label + (q.id ? "" : " · new")));
        head.appendChild(div("admin-spacer"));
        head.appendChild(button("↑", () => move(g.questions, qi, -1), "btn btn--text", "Move question up"));
        head.appendChild(button("↓", () => move(g.questions, qi, 1), "btn btn--text", "Move question down"));
        head.appendChild(button("Remove", () => { g.questions.splice(qi, 1); changed(); renderGroups(); }, "btn btn--text"));
        box.appendChild(head);

        if (typeDef(g.type).stem === "gap") {
            box.appendChild(field("Text before the gap", textInput(q.pre, (v) => { q.pre = v; changed(); }), path + ".stem"));
            box.appendChild(field("Text after the gap", textInput(q.post, (v) => { q.post = v; changed(); })));
        } else {
            box.appendChild(field(family === "choiceFixed" ? "Statement" : family === "match" ? "Prompt" : "Question",
                textInput(q.text, (v) => { q.text = v; changed(); }), path + ".stem"));
        }
        if (usesPerQuestionOptions(g.type)) box.appendChild(optionBank("Options", q.options, path + ".options", false));
        box.appendChild(answerEditor(g, q, path + ".answer"));

        const ev = textInput(q.evidenceId, (v) => { q.evidenceId = v; changed(); });
        ev.setAttribute("list", "evidenceList");
        box.appendChild(field("Evidence id (optional)", ev, path + ".evidenceId"));
        HELP_LANGUAGES.forEach((lang) => {
            const x = q.explanations[lang.code];
            const f = field("Explanation — " + lang.name + " (HTML)", textInput(x ? x.text : "", (v) => {
                if (v.trim()) q.explanations[lang.code] = { text: v, source: "HUMAN", reviewed: true };
                else delete q.explanations[lang.code];
                changed();
            }), path + ".explanations." + lang.code);
            if (x && x.source === "MACHINE" && !x.reviewed) f.appendChild(text("span", "machine", "Machine-written, not reviewed yet."));
            box.appendChild(f);
        });
        return box;
    }

    function answerEditor(g, q, path) {
        const family = familyOf(g.type);
        const set = (list) => { q.answer = list; changed(); };
        if (family === "choiceFixed") {
            const sel = d.createElement("select");
            [""].concat(typeDef(g.type).choices).forEach((c) => {
                const o = option(c, c || "— choose —");
                o.selected = q.answer[0] === c;
                sel.appendChild(o);
            });
            sel.addEventListener("change", () => set(sel.value ? [sel.value] : []));
            return field("Answer", sel, path);
        }
        if (family === "gap") {
            const input = textInput((q.answer || []).join(", "), (v) => set(v.split(",").map((a) => a.trim()).filter(Boolean)));
            const f = field("Accepted answers (separate alternatives with commas)", input, path);
            return f;
        }
        if (family === "mcSingle" || family === "match") {
            const opts = family === "match" ? g.options : q.options;
            const sel = d.createElement("select");
            sel.appendChild(option("", "— choose —"));
            opts.forEach((o) => {
                const op = option(o.key, o.key + (o.label ? " — " + o.label : ""));
                op.selected = q.answer[0] === o.key;
                sel.appendChild(op);
            });
            sel.addEventListener("change", () => set(sel.value ? [sel.value] : []));
            return field(family === "match" ? "Correct option" : "Correct answer", sel, path);
        }
        const wrap = div("field");
        wrap.dataset.field = path;
        wrap.appendChild(text("label", null, "Correct options (choose " + (g.chooseCount || "N") + ")"));
        const chosen = new Set(q.answer);
        q.options.forEach((o) => {
            const l = d.createElement("label");
            l.className = "check";
            const cb = d.createElement("input");
            cb.type = "checkbox";
            cb.value = o.key;
            cb.checked = chosen.has(o.key);
            cb.addEventListener("change", () => {
                if (cb.checked) chosen.add(o.key); else chosen.delete(o.key);
                set(q.options.map((x) => x.key).filter((k) => chosen.has(k)));
            });
            l.appendChild(cb);
            l.appendChild(d.createTextNode(" " + o.key + (o.label ? " — " + o.label : "")));
            wrap.appendChild(l);
        });
        return wrap;
    }

    function optionBank(labelText, opts, path, roman) {
        const wrap = div("opt-bank");
        wrap.dataset.field = path;
        wrap.appendChild(text("div", "opt-bank__label", labelText));
        opts.forEach((o, i) => {
            const rowEl = div("opt-row");
            rowEl.dataset.field = path + "[" + i + "].key";
            rowEl.appendChild(text("span", "opt-key", o.key));
            const input = textInput(o.label, (v) => { o.label = v; changed(); });
            input.placeholder = "Option text";
            input.setAttribute("aria-label", labelText + " " + o.key);
            rowEl.appendChild(input);
            rowEl.appendChild(button("✕", () => {
                opts.splice(i, 1);
                opts.forEach((x, j) => (x.key = optionKeyAt(j, roman)));
                changed();
                renderGroups();
            }, "btn btn--text", "Remove option " + o.key));
            wrap.appendChild(rowEl);
        });
        wrap.appendChild(button("+ Add option", () => { opts.push({ key: optionKeyAt(opts.length, roman), label: "" }); changed(); renderGroups(); }, "btn btn--text"));
        return wrap;
    }

    function move(list, index, by) {
        const to = index + by;
        if (to < 0 || to >= list.length) return;
        [list[index], list[to]] = [list[to], list[index]];
        changed();
        renderGroups();
    }

    /* ---- small DOM helpers ------------------------------------------- */
    function div(className) {
        const e = d.createElement("div");
        if (className) e.className = className;
        return e;
    }
    function text(tag, className, value) {
        const e = d.createElement(tag);
        if (className) e.className = className;
        e.textContent = value == null ? "" : value;
        return e;
    }
    function option(value, label) {
        const o = d.createElement("option");
        o.value = value;
        o.textContent = label;
        return o;
    }
    let nextControlId = 0;
    function field(labelText, control, path) {
        const wrap = div("field");
        if (path) wrap.dataset.field = path;
        const lab = text("label", null, labelText);
        if (!control.id) control.id = "ctl" + (++nextControlId);
        lab.htmlFor = control.id; // screen readers announce the label with the control
        wrap.appendChild(lab);
        wrap.appendChild(control);
        return wrap;
    }
    function checkField(labelText, checked, onChange) {
        const l = d.createElement("label");
        l.className = "check";
        const cb = d.createElement("input");
        cb.type = "checkbox";
        cb.checked = !!checked;
        cb.addEventListener("change", () => onChange(cb.checked));
        l.appendChild(cb);
        l.appendChild(d.createTextNode(" " + labelText));
        return l;
    }
    function textInput(value, onInput) {
        const input = d.createElement("input");
        input.type = "text";
        input.value = value == null ? "" : value;
        input.addEventListener("input", () => onInput(input.value));
        return input;
    }
    function numberInput(value, onInput) {
        const input = d.createElement("input");
        input.type = "number";
        input.min = "1";
        input.value = value == null ? "" : value;
        input.addEventListener("input", () => onInput(input.value));
        return input;
    }
    function button(label, onClick, cls, ariaLabel) {
        const b = d.createElement("button");
        b.type = "button";
        b.className = cls || "btn";
        b.textContent = label;
        if (ariaLabel) b.setAttribute("aria-label", ariaLabel);
        b.addEventListener("click", onClick);
        return b;
    }

    /* ---- dirty, hints, errors ---------------------------------------- */
    function changed() {
        state.dirty = true;
        els.dirtyDot.hidden = false;
        renderHints();
    }
    function clearDirty() {
        state.dirty = false;
        els.dirtyDot.hidden = true;
    }
    function isDirty() { return state.dirty; }

    function renderHints() {
        if (!els.publishHints) return;
        const hints = publishHints(state.ex);
        els.publishHints.textContent = "";
        els.publishHints.hidden = !hints.length || state.ex.status === "ARCHIVED";
        if (!hints.length) return;
        els.publishHints.appendChild(text("p", "hints__title", "Before publishing (" + hints.length + ")"));
        const ul = d.createElement("ul");
        hints.forEach((h) => ul.appendChild(text("li", null, h.message)));
        els.publishHints.appendChild(ul);
    }

    function notice(message, withReload) {
        els.editorNotice.textContent = "";
        els.editorNotice.hidden = !message;
        if (!message) return;
        els.editorNotice.appendChild(text("span", null, message + " "));
        if (withReload) {
            const reload = button("Reload", () => { clearDirty(); w.location.reload(); }, "btn");
            reload.id = "editorReload";
            els.editorNotice.appendChild(reload);
        }
    }

    /** Server errors: each one at its field (data-field = its JSON path), all of them listed at the top. */
    function showErrors(list) {
        els.root.querySelectorAll(".has-error").forEach((f) => f.classList.remove("has-error"));
        els.root.querySelectorAll(".field-err").forEach((e) => e.remove());
        els.editorErrors.textContent = "";
        if (!list || !list.length) { els.editorErrors.hidden = true; return; }
        els.editorErrors.hidden = false;
        els.editorErrors.appendChild(text("p", null, list.length === 1 ? "Please fix this:" : "Please fix these " + list.length + " things:"));
        const ul = d.createElement("ul");
        list.forEach((err) => {
            ul.appendChild(text("li", null, err.message));
            const target = err.field ? els.root.querySelector('[data-field="' + CSS.escape(err.field) + '"]') : null;
            if (target) {
                target.classList.add("has-error");
                target.appendChild(text("span", "err field-err", err.message));
            }
        });
        els.editorErrors.appendChild(ul);
        els.editorErrors.scrollIntoView && els.editorErrors.scrollIntoView({ block: "nearest" });
    }

    /* ---- signed out mid-edit: keep the work ------------------------- */
    function stashKey() { return STASH + (state.slug || "new"); }

    function handleSignedOut(e) {
        if (!e || e.status !== 401) return false;
        try { w.sessionStorage.setItem(stashKey(), JSON.stringify(state.ex)); } catch (x) { /* storage blocked */ }
        clearDirty();
        w.location.assign("/login?next=" + encodeURIComponent(w.location.pathname));
        return true;
    }

    function offerStash() {
        let saved = null;
        try { saved = w.sessionStorage.getItem(stashKey()); } catch (x) { return; }
        if (!saved) return;
        els.editorNotice.textContent = "";
        els.editorNotice.hidden = false;
        els.editorNotice.appendChild(text("span", null, "You have unsaved changes from before you were signed out. "));
        els.editorNotice.appendChild(button("Restore them", () => {
            try { w.sessionStorage.removeItem(stashKey()); } catch (x) { /* ignore */ }
            const restored = JSON.parse(saved);
            restored.version = state.ex.version; // keep the version loaded now; the server still catches conflicts
            load(restored);
            changed();
            notice("");
        }));
        els.editorNotice.appendChild(button("Discard", () => {
            try { w.sessionStorage.removeItem(stashKey()); } catch (x) { /* ignore */ }
            notice("");
        }, "btn btn--text"));
    }

    /* ---- save / publish ---------------------------------------------- */
    async function run(action) {
        if (state.busy) return false;
        state.busy = true;
        [els.saveBtn, els.publishBtn, els.unpublishBtn].forEach((b) => (b.disabled = true));
        try {
            return await action();
        } catch (e) {
            if (handleSignedOut(e)) return false;
            if (e.status === 409) {
                showErrors([]);
                notice("Someone else changed this exercise after you opened it. Reload to see their version (your unsaved changes here will be lost).", true);
            } else if ((e.status === 422 || e.status === 400) && e.errors.length) {
                showErrors(e.errors);
            } else {
                showErrors([{ field: "", message: e.status === 404 ? "This exercise no longer exists." : "Couldn't save right now. Please try again — your changes are still here." }]);
            }
            return false;
        } finally {
            state.busy = false;
            [els.saveBtn, els.publishBtn, els.unpublishBtn].forEach((b) => (b.disabled = state.ex.status === "ARCHIVED"));
        }
    }

    async function saveNow() {
        const input = toInput();
        let view;
        if (state.slug) {
            view = await api.update(state.slug, input);
        } else {
            view = await api.create(input);
            state.slug = view.id;
            els.root.dataset.sessionId = view.id;
            if (w.history && w.history.replaceState) w.history.replaceState(null, "", "/admin/sessions/" + encodeURIComponent(view.id) + "/edit");
        }
        load(view);
        showErrors([]);
        notice("");
        return true;
    }

    function save() {
        if (state.ex.status === "PUBLISHED" && w.confirm
                && !w.confirm("Learners see this exercise. Saving creates revision " + (state.ex.revision + 1) + "; past attempts keep the old one. Save?")) {
            return Promise.resolve(false);
        }
        return run(saveNow);
    }

    function publish() {
        return run(async () => {
            if (state.dirty || !state.slug) await saveNow();
            load(await api.publish(state.slug));
            notice("Published: learners can now open /reading/" + state.slug + ".");
            return true;
        });
    }

    function unpublish() {
        return run(async () => {
            load(await api.unpublish(state.slug));
            notice("Unpublished: learners no longer see it.");
            return true;
        });
    }

    /* ---- events ------------------------------------------------------- */
    function wireStaticEvents() {
        const bind = (el, key, convert) => el && el.addEventListener("input", () => {
            state.ex[key] = convert ? convert(el.value) : el.value;
            changed();
        });
        bind(els.slug, "id");
        bind(els.title, "title");
        bind(els.durationSeconds, "durationSeconds", numberOrNull);
        bind(els.lowTimeThresholdSeconds, "lowTimeThresholdSeconds", numberOrNull);
        bind(els.stageId, "stageId", numberOrNull);
        bind(els.position, "position", numberOrNull);
        bind(els.topicIds, "topicIds", (v) => v.split(",").map((x) => Number(x.trim())).filter((x) => x > 0));
        els.format.addEventListener("change", () => { state.ex.format = els.format.value; changed(); });
        els.level.addEventListener("change", () => { state.ex.level = els.level.value || null; changed(); });

        els.saveBtn.addEventListener("click", save);
        els.publishBtn.addEventListener("click", publish);
        els.unpublishBtn.addEventListener("click", unpublish);
        const on = (id, fn) => { const b = byId(id); if (b) b.addEventListener("click", fn); };
        on("glossBtn", () => {
            if (!state.passage.addGloss()) notice("Select a word in the passage first, then press \"Add gloss\".");
        });
        on("evidenceBtn", () => {
            if (!state.passage.markEvidence()) notice("Select the sentence that proves an answer, then press \"Mark evidence\".");
        });
        on("removeMarkBtn", () => state.passage.removeMarkAtSelection());
    }

    return { init, save, publish, unpublish, isDirty, _toInput: toInput, _state: state };
}
