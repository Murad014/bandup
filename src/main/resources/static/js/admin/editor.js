/* =========================================================================
   editor.js — session editor controller (catalog-driven questions).
   A group has a question TYPE from questionTypes.js; the editor renders the
   right authoring fields per family. Internally each question carries its own
   answer/options/evidence/explanation; on save it splits into
   {questionGroups, answerKey} with contiguous numbering.
   ========================================================================= */

import { createPassageEditor } from "./passage.js";
import { validate } from "./validate.js";
import { QUESTION_TYPES, typeDef, familyOf, orderedTypeKeys, usesGroupOptions, usesPerQuestionOptions, optionKeyAt } from "./questionTypes.js";

function blankSession() {
    return { status: "draft", title: "", stageLabel: "", durationSeconds: 300, lowThreshold: 60, passageHtml: "", groups: [] };
}
function splitAccepted(value) {
    if (Array.isArray(value)) return value.filter((v) => String(v).trim() !== "");
    return String(value || "").split(",").map((v) => v.trim()).filter((v) => v !== "");
}
function reassignKeys(options) { options.forEach((o, i) => { o.key = optionKeyAt(i); }); }

export function createEditor(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const api = options.api;

    const els = {};
    const state = { id: null, session: blankSession(), dirty: false, passage: null };
    const byId = (id) => d.getElementById(id);

    /* ---- new model pieces -------------------------------------------- */
    function newQuestion(typeKey) {
        const fam = familyOf(typeKey);
        const def = typeDef(typeKey);
        const q = { evidenceId: "", explanation: "" };
        if (fam === "choiceFixed") { q.text = ""; q.answer = ""; }
        else if (fam === "gap") { if (def.stem === "prompt") q.prompt = ""; else { q.pre = ""; q.post = ""; } q.answer = ""; }
        else if (fam === "mcSingle") { q.text = ""; q.options = seedOptions(3); q.answer = ""; }
        else if (fam === "mcMulti") { q.text = ""; q.options = seedOptions(4); q.answer = []; }
        else if (fam === "match") { q.prompt = ""; q.answer = ""; }
        return q;
    }
    function seedOptions(n) {
        const arr = [];
        for (let i = 0; i < n; i++) arr.push({ key: optionKeyAt(i), label: "" });
        return arr;
    }
    function newGroup(typeKey) {
        const g = { type: typeKey, heading: "", instruction: "", options: [], chooseCount: "", questions: [newQuestion(typeKey)] };
        if (usesGroupOptions(typeKey)) g.options = seedOptions(4);
        return g;
    }

    /* ---- lifecycle ---------------------------------------------------- */
    async function init() {
        els.root = byId("editorRoot");
        if (!els.root) return;
        els.title = byId("title");
        els.stageLabel = byId("stageLabel");
        els.duration = byId("durationSeconds");
        els.lowThreshold = byId("lowThresholdSeconds");
        els.groups = byId("groups");
        els.errors = byId("editorErrors");
        els.statusBadge = byId("statusBadge");
        els.dirtyDot = byId("dirtyDot");
        els.evidenceList = byId("evidenceList");

        state.passage = createPassageEditor({
            doc: d, win: w,
            editEl: byId("passageEdit"),
            previewEl: byId("passagePreview"),
            onChange: (html) => { state.session.passageHtml = html; refreshEvidenceList(); markDirty(); },
        });

        wireStaticEvents();

        const id = els.root.dataset.sessionId;
        if (id) {
            try { fromApi(await api.get(id)); } catch (e) { showErrors([{ field: "", message: "Couldn't load this session." }]); return; }
        } else { state.session = blankSession(); state.id = null; }

        populateMetadata();
        state.passage.setHtml(state.session.passageHtml);
        refreshEvidenceList();
        renderGroups();
        renderStatus();
        clearDirty();

        const nav = w.BandUpAdminNav;
        if (nav && nav.registerGuard) nav.registerGuard(() => state.dirty);
    }

    /* ---- model <-> api ------------------------------------------------ */
    function fromApi(apiSession) {
        state.id = apiSession.id || null;
        const s = blankSession();
        s.status = apiSession.status || "draft";
        s.title = apiSession.title || "";
        s.stageLabel = apiSession.stageLabel || "";
        s.durationSeconds = apiSession.durationSeconds || 300;
        s.lowThreshold = apiSession.lowTimeThresholdSeconds || 60;
        s.passageHtml = apiSession.passageHtml || "";
        const akByN = new Map((apiSession.answerKey || []).map((a) => [a.n, a]));
        (apiSession.questionGroups || []).forEach((g) => {
            const typeKey = QUESTION_TYPES[g.type] ? g.type : "TFNG";
            const fam = familyOf(typeKey);
            const def = typeDef(typeKey);
            const group = {
                type: typeKey, heading: g.heading || "", instruction: g.instruction || "",
                options: Array.isArray(g.options) ? g.options.map((o) => ({ key: o.key, label: o.label })) : [],
                chooseCount: g.chooseCount || "",
                questions: [],
            };
            (g.questions || []).forEach((q) => {
                const ak = akByN.get(q.n) || {};
                const nq = { evidenceId: ak.evidenceId || "", explanation: ak.explanation || "" };
                if (fam === "choiceFixed") { nq.text = q.text || ""; nq.answer = ak.answer || ""; }
                else if (fam === "gap") { if (def.stem === "prompt") nq.prompt = q.prompt || ""; else { nq.pre = q.pre || ""; nq.post = q.post || ""; } nq.answer = Array.isArray(ak.answer) ? ak.answer.join(", ") : (ak.answer || ""); }
                else if (fam === "mcSingle") { nq.text = q.text || ""; nq.options = (q.options || seedOptions(3)).map((o) => ({ key: o.key, label: o.label })); nq.answer = ak.answer || ""; }
                else if (fam === "mcMulti") { nq.text = q.text || ""; nq.options = (q.options || seedOptions(4)).map((o) => ({ key: o.key, label: o.label })); nq.answer = Array.isArray(ak.answer) ? ak.answer.slice() : splitAccepted(ak.answer); }
                else if (fam === "match") { nq.prompt = q.prompt || ""; nq.answer = ak.answer || ""; }
                group.questions.push(nq);
            });
            s.groups.push(group);
        });
        state.session = s;
    }

    function toApi() {
        let counter = 1;
        const questionGroups = [];
        const answerKey = [];
        state.session.groups.forEach((g) => {
            const fam = familyOf(g.type);
            const def = typeDef(g.type);
            const qs = [];
            g.questions.forEach((q) => {
                const n = counter++;
                const qOut = { n, type: g.type };
                const ak = { n };
                if (fam === "choiceFixed") { qOut.text = q.text || ""; ak.answer = q.answer || ""; }
                else if (fam === "gap") { if (def.stem === "prompt") qOut.prompt = q.prompt || ""; else { qOut.pre = q.pre || ""; qOut.post = q.post || ""; } ak.answer = splitAccepted(q.answer); }
                else if (fam === "mcSingle") { qOut.text = q.text || ""; qOut.options = cleanOptions(q.options); ak.answer = q.answer || ""; }
                else if (fam === "mcMulti") { qOut.text = q.text || ""; qOut.options = cleanOptions(q.options); ak.answer = Array.isArray(q.answer) ? q.answer.slice() : splitAccepted(q.answer); }
                else if (fam === "match") { qOut.prompt = q.prompt || ""; ak.answer = q.answer || ""; }
                if (q.evidenceId) ak.evidenceId = q.evidenceId;
                ak.explanation = q.explanation || "";
                qs.push(qOut);
                answerKey.push(ak);
            });
            const gOut = { type: g.type, heading: g.heading || "", instruction: g.instruction || "", questions: qs };
            if (usesGroupOptions(g.type)) gOut.options = cleanOptions(g.options);
            if (fam === "mcMulti" && String(g.chooseCount).trim() !== "") gOut.chooseCount = Number(g.chooseCount);
            questionGroups.push(gOut);
        });
        const out = {
            status: state.session.status || "draft",
            title: state.session.title, stageLabel: state.session.stageLabel,
            durationSeconds: Number(state.session.durationSeconds),
            lowTimeThresholdSeconds: Number(state.session.lowThreshold),
            passageHtml: state.session.passageHtml || "",
            questionGroups, answerKey,
        };
        if (state.id) out.id = state.id;
        return out;
    }
    function cleanOptions(opts) {
        return (opts || []).map((o) => ({ key: o.key, label: o.label || "" }));
    }

    /* ---- rendering ---------------------------------------------------- */
    function populateMetadata() {
        els.title.value = state.session.title;
        els.stageLabel.value = state.session.stageLabel;
        els.duration.value = state.session.durationSeconds;
        els.lowThreshold.value = state.session.lowThreshold;
    }
    function refreshEvidenceList() {
        if (!els.evidenceList) return;
        els.evidenceList.textContent = "";
        state.passage.evidenceIds().forEach((id) => { const o = d.createElement("option"); o.value = id; els.evidenceList.appendChild(o); });
    }
    function renderStatus() {
        if (els.statusBadge) {
            els.statusBadge.textContent = state.session.status;
            els.statusBadge.className = "badge badge--" + (state.session.status === "published" ? "published" : "draft");
        }
    }

    function renderGroups() {
        els.groups.textContent = "";
        let start = 1;
        state.session.groups.forEach((g, gi) => {
            els.groups.appendChild(groupCard(g, gi, start));
            start += g.questions.length;
        });
        const addRow = d.createElement("div");
        addRow.className = "admin-row";
        addRow.appendChild(button("+ Add question group", () => { state.session.groups.push(newGroup("TFNG")); markDirty(); renderGroups(); }, "btn btn--primary"));
        els.groups.appendChild(addRow);
    }

    function groupCard(g, gi, start) {
        const card = d.createElement("div");
        card.className = "card";

        // Type selector.
        const typeSel = d.createElement("select");
        orderedTypeKeys().forEach((k) => { const o = d.createElement("option"); o.value = k; o.textContent = QUESTION_TYPES[k].label; if (k === g.type) o.selected = true; typeSel.appendChild(o); });
        typeSel.addEventListener("change", () => { changeGroupType(gi, typeSel.value); });
        card.appendChild(field("Question type", typeSel));

        card.appendChild(field("Heading", textInput(g.heading, (v) => { g.heading = v; markDirty(); })));
        card.appendChild(field("Instruction (HTML)", textInput(g.instruction, (v) => { g.instruction = v; markDirty(); })));

        if (usesGroupOptions(g.type)) {
            card.appendChild(optionBank(typeDef(g.type).optionsLabel || "Options", g.options, () => { markDirty(); renderGroups(); }, () => markDirty()));
        }
        if (familyOf(g.type) === "mcMulti") {
            card.appendChild(field("How many answers to choose (optional)", numberInput(g.chooseCount, (v) => { g.chooseCount = v; markDirty(); })));
        }

        g.questions.forEach((q, qi) => card.appendChild(questionCard(g, gi, q, qi, start + qi)));

        const row = d.createElement("div");
        row.className = "admin-row";
        row.appendChild(button("+ Add question", () => { g.questions.push(newQuestion(g.type)); markDirty(); renderGroups(); }));
        row.appendChild(button("Remove group", () => { state.session.groups.splice(gi, 1); markDirty(); renderGroups(); }, "btn btn--danger"));
        card.appendChild(row);
        return card;
    }

    function changeGroupType(gi, typeKey) {
        const g = state.session.groups[gi];
        g.type = typeKey;
        g.options = usesGroupOptions(typeKey) ? seedOptions(4) : [];
        g.chooseCount = "";
        g.questions = [newQuestion(typeKey)];
        markDirty();
        renderGroups();
    }

    function questionCard(g, gi, q, qi, number) {
        const fam = familyOf(g.type);
        const def = typeDef(g.type);
        const box = d.createElement("div");
        box.className = "q-card";

        const head = d.createElement("div");
        head.className = "q-head";
        const num = d.createElement("span");
        num.className = "q-num";
        num.textContent = "Q" + number;
        head.appendChild(num);
        head.appendChild(button("Remove", () => { g.questions.splice(qi, 1); markDirty(); renderGroups(); }, "btn btn--text"));
        box.appendChild(head);

        // Stem.
        if (def.stem === "statement") box.appendChild(field("Statement", textInput(q.text, (v) => { q.text = v; markDirty(); })));
        else if (def.stem === "question") box.appendChild(field("Question", textInput(q.text, (v) => { q.text = v; markDirty(); })));
        else if (def.stem === "prompt") box.appendChild(field("Prompt / stem", textInput(q.prompt, (v) => { q.prompt = v; markDirty(); })));
        else if (def.stem === "prepost") {
            box.appendChild(field("Sentence (before gap)", textInput(q.pre, (v) => { q.pre = v; markDirty(); })));
            box.appendChild(field("Sentence (after gap)", textInput(q.post, (v) => { q.post = v; markDirty(); })));
        }

        // Per-question options (MC).
        if (usesPerQuestionOptions(g.type)) {
            box.appendChild(optionBank("Options", q.options, () => { markDirty(); renderGroups(); }, () => markDirty()));
        }

        // Answer editor.
        box.appendChild(answerEditor(g, q));

        // Evidence + explanation.
        const ev = textInput(q.evidenceId, (v) => { q.evidenceId = v; markDirty(); });
        ev.setAttribute("list", "evidenceList");
        box.appendChild(field("Evidence id (optional)", ev));
        box.appendChild(field("Explanation (HTML)", textInput(q.explanation, (v) => { q.explanation = v; markDirty(); })));
        return box;
    }

    function answerEditor(g, q) {
        const fam = familyOf(g.type);
        const def = typeDef(g.type);
        if (fam === "choiceFixed") {
            const sel = d.createElement("select");
            [""].concat(def.choices).forEach((o) => { const op = d.createElement("option"); op.value = o; op.textContent = o || "— choose —"; if (q.answer === o) op.selected = true; sel.appendChild(op); });
            sel.addEventListener("change", () => { q.answer = sel.value; markDirty(); });
            return field("Answer", sel);
        }
        if (fam === "gap") {
            return field("Accepted answers (comma-separated)", textInput(q.answer, (v) => { q.answer = v; markDirty(); }));
        }
        if (fam === "mcSingle" || fam === "match") {
            const opts = fam === "match" ? g.options : q.options;
            const sel = d.createElement("select");
            const empty = d.createElement("option"); empty.value = ""; empty.textContent = "— choose —"; sel.appendChild(empty);
            (opts || []).forEach((o) => { const op = d.createElement("option"); op.value = o.key; op.textContent = o.key + (o.label ? " — " + o.label : ""); if (q.answer === o.key) op.selected = true; sel.appendChild(op); });
            sel.addEventListener("change", () => { q.answer = sel.value; markDirty(); });
            return field("Correct option", sel);
        }
        if (fam === "mcMulti") {
            const wrap = d.createElement("div");
            wrap.className = "field";
            const lab = d.createElement("label"); lab.textContent = "Correct options"; wrap.appendChild(lab);
            const set = new Set(Array.isArray(q.answer) ? q.answer : []);
            (q.options || []).forEach((o) => {
                const l = d.createElement("label"); l.className = "check";
                const cb = d.createElement("input"); cb.type = "checkbox"; cb.value = o.key; cb.checked = set.has(o.key);
                cb.addEventListener("change", () => {
                    if (cb.checked) set.add(o.key); else set.delete(o.key);
                    q.answer = [...set];
                    markDirty();
                });
                l.appendChild(cb); l.appendChild(d.createTextNode(" " + o.key + (o.label ? " — " + o.label : "")));
                wrap.appendChild(l);
            });
            return wrap;
        }
        return d.createElement("div");
    }

    /* ---- option bank editor ------------------------------------------ */
    function optionBank(labelText, opts, onStructural, onEdit) {
        const wrap = d.createElement("div");
        wrap.className = "opt-bank";
        const lab = d.createElement("div"); lab.className = "opt-bank__label"; lab.textContent = labelText; wrap.appendChild(lab);
        opts.forEach((o, i) => {
            const rowEl = d.createElement("div"); rowEl.className = "opt-row";
            const key = d.createElement("span"); key.className = "opt-key"; key.textContent = o.key; rowEl.appendChild(key);
            const input = d.createElement("input"); input.type = "text"; input.value = o.label || ""; input.placeholder = "Option text";
            input.addEventListener("input", () => { o.label = input.value; onEdit(); });
            rowEl.appendChild(input);
            const rm = button("✕", () => { opts.splice(i, 1); reassignKeys(opts); onStructural(); }, "btn btn--text");
            rowEl.appendChild(rm);
            wrap.appendChild(rowEl);
        });
        wrap.appendChild(button("+ Add option", () => { opts.push({ key: optionKeyAt(opts.length), label: "" }); onStructural(); }, "btn btn--text"));
        return wrap;
    }

    /* ---- small DOM helpers ------------------------------------------- */
    function field(labelText, control) {
        const wrap = d.createElement("div"); wrap.className = "field";
        const lab = d.createElement("label"); lab.textContent = labelText; wrap.appendChild(lab);
        wrap.appendChild(control); return wrap;
    }
    function textInput(value, onInput) {
        const input = d.createElement("input"); input.type = "text"; input.value = value == null ? "" : value;
        input.addEventListener("input", () => onInput(input.value)); return input;
    }
    function numberInput(value, onInput) {
        const input = d.createElement("input"); input.type = "number"; input.min = "1"; input.value = value == null ? "" : value;
        input.addEventListener("input", () => onInput(input.value)); return input;
    }
    function button(label, onClick, cls) {
        const b = d.createElement("button"); b.type = "button"; b.className = cls || "btn"; b.textContent = label;
        b.addEventListener("click", onClick); return b;
    }

    /* ---- dirty + validation ------------------------------------------ */
    function markDirty() { state.dirty = true; if (els.dirtyDot) els.dirtyDot.hidden = false; }
    function clearDirty() { state.dirty = false; if (els.dirtyDot) els.dirtyDot.hidden = true; }
    function isDirty() { return state.dirty; }

    function showErrors(list) {
        if (!els.errors) return;
        els.errors.textContent = "";
        [els.title, els.stageLabel, els.duration, els.lowThreshold].forEach((e) => e && e.closest(".field") && e.closest(".field").classList.remove("has-error"));
        if (!list || list.length === 0) { els.errors.hidden = true; return; }
        els.errors.hidden = false;
        const ul = d.createElement("ul");
        const map = { title: els.title, stageLabel: els.stageLabel, durationSeconds: els.duration, lowTimeThresholdSeconds: els.lowThreshold };
        list.forEach((err) => {
            const li = d.createElement("li"); li.textContent = (err.field ? err.field + ": " : "") + err.message; ul.appendChild(li);
            const f = map[err.field]; if (f && f.closest(".field")) f.closest(".field").classList.add("has-error");
        });
        els.errors.appendChild(ul);
    }

    /* ---- save / publish ---------------------------------------------- */
    async function persist(forPublish) {
        const apiSession = toApi();
        const res = validate(apiSession, { forPublish });
        showErrors(res.errors);
        if (!res.ok) return false;
        try {
            const saved = state.id ? await api.update(state.id, apiSession) : await api.create(apiSession);
            state.id = saved.id; state.session.status = saved.status;
            if (forPublish) { const pub = await api.publish(state.id); state.session.status = pub.status; }
            renderStatus(); clearDirty(); return true;
        } catch (e) {
            const serverErrors = e && e.body && e.body.errors ? e.body.errors : [{ field: "", message: "Save failed. Please try again." }];
            showErrors(serverErrors); return false;
        }
    }
    function save() { return persist(false); }
    function publish() { return persist(true); }
    async function unpublish() {
        if (!state.id) return false;
        const res = await api.unpublish(state.id); state.session.status = res.status; renderStatus(); return true;
    }

    /* ---- events ------------------------------------------------------- */
    function wireStaticEvents() {
        const bind = (el, key, num) => el && el.addEventListener("input", () => { state.session[key] = num ? Number(el.value) : el.value; markDirty(); });
        bind(els.title, "title"); bind(els.stageLabel, "stageLabel");
        bind(els.duration, "durationSeconds", true); bind(els.lowThreshold, "lowThreshold", true);

        const on = (id, fn) => { const b = byId(id); if (b) b.addEventListener("click", fn); };
        on("saveBtn", save); on("publishBtn", publish); on("unpublishBtn", unpublish);
        on("glossBtn", () => { const az = w.prompt ? w.prompt("Azerbaijani meaning:") : ""; if (az != null) state.passage.addGloss(az); });
        on("evidenceBtn", () => state.passage.markEvidence());
        on("removeMarkBtn", () => state.passage.removeMarkAtSelection());
    }

    return {
        init, save, publish, unpublish, isDirty,
        _toApi: toApi, _fromApi: fromApi, _state: state, _render: renderGroups,
    };
}
