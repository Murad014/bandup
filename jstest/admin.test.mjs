import { test } from "node:test";
import assert from "node:assert/strict";
import { JSDOM } from "jsdom";

const BASE = "../src/main/resources/static/js/admin/";
const { validate, evidenceIdsIn } = await import(BASE + "validate.js");
const { createStubApi } = await import(BASE + "api.js");
const { SEED } = await import(BASE + "fixtures.js");
const { createPassageEditor } = await import(BASE + "passage.js");
const { createEditor } = await import(BASE + "editor.js");
const { createSessionsPage } = await import(BASE + "sessions.js");
const QT = await import(BASE + "questionTypes.js");

/* ----------------------------- validate ------------------------------ */
test("evidenceIdsIn extracts ids regardless of attribute order", () => {
    assert.deepEqual(evidenceIdsIn('<span class="ev" id="e1">x</span> <span id="e2" class="ev">y</span>'), ["e1", "e2"]);
});

test("validate: seeded session passes publish validation", () => {
    const r = validate(SEED[0], { forPublish: true });
    assert.equal(r.ok, true, JSON.stringify(r.errors));
});

test("validate: metadata errors always block", () => {
    const s = structuredClone(SEED[0]);
    s.title = "";
    s.lowTimeThresholdSeconds = 9999; // > duration
    const r = validate(s, { forPublish: false });
    assert.equal(r.ok, false);
    assert.ok(r.errors.some((e) => e.field === "title"));
    assert.ok(r.errors.some((e) => e.field === "lowTimeThresholdSeconds"));
});

test("validate: publish enforces answers, numbering, evidence; draft relaxes", () => {
    const s = structuredClone(SEED[0]);
    s.answerKey = s.answerKey.filter((a) => a.n !== 3); // remove an answer
    s.answerKey.find((a) => a.n === 4).answer = [];      // empty FILL
    s.answerKey.find((a) => a.n === 5).evidenceId = "e99"; // missing evidence
    const pub = validate(s, { forPublish: true });
    assert.equal(pub.ok, false);
    assert.ok(pub.errors.some((e) => e.field.startsWith("answerKey[3]")));
    assert.ok(pub.errors.some((e) => e.field === "answerKey[4].answer"));
    assert.ok(pub.errors.some((e) => e.field === "answerKey[5].evidenceId"));
    // Draft only blocks on metadata — these content issues are not blocking.
    const draft = validate(s, { forPublish: false });
    assert.equal(draft.ok, true, JSON.stringify(draft.errors));
});

/* ------------------------------- api --------------------------------- */
test("stub api: CRUD round-trip; get includes answers; 404; create assigns id", async () => {
    const api = createStubApi(SEED);
    const list = await api.list();
    assert.equal(list.length, 1);
    assert.equal(list[0].questionCount, 6);

    const full = await api.get("yellowstone-wolves");
    assert.ok(Array.isArray(full.answerKey) && full.answerKey.length === 6);

    await assert.rejects(() => api.get("nope"), (e) => e.status === 404);

    const created = await api.create({ title: "New Reading!", questionGroups: [], answerKey: [] });
    assert.ok(created.id);
    assert.equal(created.status, "draft");
    assert.equal((await api.list()).length, 2);

    const pub = await api.publish(created.id);
    assert.equal(pub.status, "published");
    await api.remove(created.id);
    assert.equal((await api.list()).length, 1);
});

/* ----------------------------- passage ------------------------------- */
function domWith(html) {
    const dom = new JSDOM("<!DOCTYPE html><body>" + html + "</body>");
    return dom;
}

test("passage: addGloss wraps a range; evidence allocates unique ids; serialize cleans", () => {
    const dom = domWith('<div id="e"></div><div id="p"></div>');
    const d = dom.window.document;
    const editEl = d.getElementById("e");
    const previewEl = d.getElementById("p");
    const pe = createPassageEditor({ doc: d, win: dom.window, editEl, previewEl });

    pe.setHtml("hello world");
    // gloss "world"
    const r = d.createRange();
    const textNode = editEl.firstChild;
    r.setStart(textNode, 6); r.setEnd(textNode, 11);
    pe.applyGloss(r, "dünya");
    const w = editEl.querySelector(".w");
    assert.ok(w && w.getAttribute("data-az") === "dünya" && w.textContent === "world");

    // two evidence spans -> e1, e2
    const r2 = d.createRange(); r2.selectNodeContents(editEl);
    const id1 = pe.applyEvidence(r2);
    assert.equal(id1, "e1");
    assert.deepEqual(pe.evidenceIds(), ["e1"]);

    // serialize strips disallowed tags/attrs
    pe.setHtml('<div style="x"><b>keep</b></div>');
    const out = pe.serialize();
    assert.equal(out.includes("<div"), false);
    assert.equal(out.includes("style"), false);
    assert.ok(out.includes("<b>keep</b>"));
});

test("passage: nextEvidenceId accounts for existing ids", () => {
    const dom = domWith('<div id="e"></div>');
    const d = dom.window.document;
    const pe = createPassageEditor({ doc: d, win: dom.window, editEl: d.getElementById("e") });
    pe.setHtml('<span class="ev" id="e1">a</span><span class="ev" id="e3">b</span>');
    assert.equal(pe.nextEvidenceId(), "e4");
});

/* ------------------------------ editor ------------------------------- */
const EDITOR_HTML = `
<main id="editorRoot" data-session-id="yellowstone-wolves">
  <div id="statusBadge"></div><span id="dirtyDot" hidden></span>
  <div id="editorErrors" hidden></div>
  <div class="field"><label>Title</label><input id="title" type="text"></div>
  <div class="field"><label>Stage</label><input id="stageLabel" type="text"></div>
  <div class="field"><label>Dur</label><input id="durationSeconds" type="number"></div>
  <div class="field"><label>Low</label><input id="lowThresholdSeconds" type="number"></div>
  <div id="passageEdit"></div><div id="passagePreview"></div>
  <div id="groups"></div>
  <datalist id="evidenceList"></datalist>
  <button id="saveBtn"></button><button id="publishBtn"></button><button id="unpublishBtn"></button>
  <button id="glossBtn"></button><button id="evidenceBtn"></button><button id="removeMarkBtn"></button>
</main>`;

function editorDom(sessionId) {
    const html = sessionId === null ? EDITOR_HTML.replace(' data-session-id="yellowstone-wolves"', "") : EDITOR_HTML;
    const dom = new JSDOM("<!DOCTYPE html><body>" + html + "</body>", { url: "http://localhost/admin/sessions/x/edit" });
    const nav = { _guard: null, registerGuard(fn) { this._guard = fn; }, clearGuard() { this._guard = null; } };
    dom.window.BandUpAdminNav = nav;
    return { dom, nav };
}

test("editor: loads a session, populates metadata and groups, not dirty", async () => {
    const { dom } = editorDom();
    const api = createStubApi(SEED);
    const ed = createEditor({ api, doc: dom.window.document, win: dom.window });
    await ed.init();
    assert.equal(dom.window.document.getElementById("title").value, "The Return of the Wolves");
    assert.equal(dom.window.document.querySelectorAll("#groups .card").length, 2);
    assert.equal(ed.isDirty(), false);
    const apiShape = ed._toApi();
    assert.equal(apiShape.questionGroups.length, 2);
    assert.equal(apiShape.answerKey.length, 6);
    assert.deepEqual(apiShape.answerKey.map((a) => a.n), [1, 2, 3, 4, 5, 6]);
});

test("editor: editing marks dirty and registers the nav guard; publish clears dirty", async () => {
    const { dom, nav } = editorDom();
    const api = createStubApi(SEED);
    const ed = createEditor({ api, doc: dom.window.document, win: dom.window });
    await ed.init();

    const title = dom.window.document.getElementById("title");
    title.value = "Edited title";
    title.dispatchEvent(new dom.window.Event("input"));
    assert.equal(ed.isDirty(), true);
    assert.equal(typeof nav._guard, "function");
    assert.equal(nav._guard(), true);

    const ok = await ed.publish();
    assert.equal(ok, true);
    assert.equal(ed.isDirty(), false);
    assert.equal(ed._state.session.status, "published");
});

test("editor: new blank session is blocked from publishing with field errors", async () => {
    const { dom } = editorDom(null);
    const api = createStubApi(SEED);
    const ed = createEditor({ api, doc: dom.window.document, win: dom.window });
    await ed.init();
    const ok = await ed.publish();
    assert.equal(ok, false);
    assert.equal(dom.window.document.getElementById("editorErrors").hidden, false);
});

/* ----------------------------- sessions ------------------------------ */
const SESSIONS_HTML = `
<main id="sessionsRoot">
  <p id="sessionsStatus"></p>
  <table id="sessionsTable" hidden><tbody id="sessionsBody"></tbody></table>
  <div id="sessionsEmpty" hidden></div>
</main>`;

function sessionsDom() {
    const dom = new JSDOM("<!DOCTYPE html><body>" + SESSIONS_HTML + "</body>");
    return dom;
}

test("sessions: renders rows with status and publish toggle label", async () => {
    const dom = sessionsDom();
    const api = createStubApi(SEED);
    const page = createSessionsPage({ api, doc: dom.window.document, win: dom.window });
    await page.init();
    const rows = dom.window.document.querySelectorAll("#sessionsBody tr");
    assert.equal(rows.length, 1);
    assert.ok(rows[0].textContent.includes("The Return of the Wolves"));
    assert.ok(rows[0].querySelector('button[data-action="unpublish"]'));
});

test("sessions: delete asks for confirmation then removes the row", async () => {
    const dom = sessionsDom();
    dom.window.confirm = () => true;
    const api = createStubApi(SEED);
    const page = createSessionsPage({ api, doc: dom.window.document, win: dom.window });
    await page.init();
    const del = dom.window.document.querySelector('#sessionsBody button[data-action="delete"]');
    del.dispatchEvent(new dom.window.MouseEvent("click", { bubbles: true }));
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));
    assert.equal(dom.window.document.getElementById("sessionsEmpty").hidden, false);
});

test("sessions: publish toggle unpublishes a published session", async () => {
    const dom = sessionsDom();
    const api = createStubApi(SEED);
    const page = createSessionsPage({ api, doc: dom.window.document, win: dom.window });
    await page.init();
    const btn = dom.window.document.querySelector('#sessionsBody button[data-action="unpublish"]');
    btn.dispatchEvent(new dom.window.MouseEvent("click", { bubbles: true }));
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));
    const badge = dom.window.document.querySelector("#sessionsBody .badge");
    assert.equal(badge.textContent, "draft");
});

const LIB_HTML = `
<main id="sessionsRoot">
  <input id="sessionsSearch"><select id="sessionsFilter">
    <option value="all">all</option><option value="published">published</option><option value="draft">draft</option>
  </select><select id="sessionsSort"><option value="title">t</option></select>
  <span id="sessionsCount"></span>
  <p id="sessionsStatus"></p>
  <table id="sessionsTable" hidden><tbody id="sessionsBody"></tbody></table>
  <p id="sessionsNote" hidden></p>
  <div id="sessionsEmpty" hidden></div>
</main>`;

function minimalSession(id, title, status) {
    return {
        id, title, status, stageLabel: "Stage",
        durationSeconds: 300, lowTimeThresholdSeconds: 60, passageHtml: "",
        questionGroups: [{ type: "TFNG", heading: "", instruction: "", questions: [{ n: 1, type: "TFNG", text: "" }] }],
        answerKey: [{ n: 1, answer: "TRUE" }],
    };
}

test("sessions library: search, status filter, and count work", async () => {
    const dom = new JSDOM("<!DOCTYPE html><body>" + LIB_HTML + "</body>");
    const api = createStubApi([minimalSession("alpha", "Alpha Reading", "published"), minimalSession("beta", "Beta Reading", "draft")]);
    const page = createSessionsPage({ api, doc: dom.window.document, win: dom.window });
    await page.init();
    const doc = dom.window.document;

    assert.equal(doc.querySelectorAll("#sessionsBody tr").length, 2);
    assert.equal(doc.getElementById("sessionsCount").textContent, "2 of 2");

    // Filter: published only -> 1 row.
    const filter = doc.getElementById("sessionsFilter");
    filter.value = "published";
    filter.dispatchEvent(new dom.window.Event("change"));
    assert.equal(doc.querySelectorAll("#sessionsBody tr").length, 1);

    // Search 'beta' with filter back to all -> 1 row (Beta).
    filter.value = "all"; filter.dispatchEvent(new dom.window.Event("change"));
    const search = doc.getElementById("sessionsSearch");
    search.value = "beta";
    search.dispatchEvent(new dom.window.Event("input"));
    const rows = doc.querySelectorAll("#sessionsBody tr");
    assert.equal(rows.length, 1);
    assert.ok(rows[0].textContent.includes("Beta"));

    // No match -> note shown, table hidden.
    search.value = "zzz"; search.dispatchEvent(new dom.window.Event("input"));
    assert.equal(doc.getElementById("sessionsNote").hidden, false);
    assert.equal(doc.getElementById("sessionsTable").hidden, true);
});


/* --------------------------- question types -------------------------- */
test("catalog: families map correctly and cover IELTS types", () => {
    assert.equal(QT.familyOf("TFNG"), "choiceFixed");
    assert.equal(QT.familyOf("YNNG"), "choiceFixed");
    assert.equal(QT.familyOf("MCQ"), "mcSingle");
    assert.equal(QT.familyOf("MCMA"), "mcMulti");
    assert.equal(QT.familyOf("FILL"), "gap");
    assert.equal(QT.familyOf("SHORT"), "gap");
    assert.equal(QT.familyOf("MATCH_HEADINGS"), "match");
    assert.ok(QT.orderedTypeKeys().length >= 10);
    assert.equal(QT.usesGroupOptions("MATCH_FEATURES"), true);
    assert.equal(QT.usesPerQuestionOptions("MCQ"), true);
});

function baseSession(extra) {
    return Object.assign({
        title: "T", stageLabel: "S", durationSeconds: 300, lowTimeThresholdSeconds: 60, passageHtml: "",
        questionGroups: [], answerKey: [],
    }, extra);
}

test("validate: multiple-choice (single) enforces a valid option", () => {
    const mcq = baseSession({
        questionGroups: [{ type: "MCQ", heading: "", instruction: "", questions: [{ n: 1, type: "MCQ", text: "Which?", options: [{ key: "A", label: "a" }, { key: "B", label: "b" }] }] }],
        answerKey: [{ n: 1, answer: "B", explanation: "" }],
    });
    assert.equal(validate(mcq, { forPublish: true }).ok, true);

    mcq.answerKey[0].answer = "Z"; // not an option
    assert.equal(validate(mcq, { forPublish: true }).ok, false);
});

test("validate: matching requires an option bank and a valid match", () => {
    const match = baseSession({
        questionGroups: [{
            type: "MATCH_HEADINGS", heading: "", instruction: "",
            options: [{ key: "A", label: "Heading one" }, { key: "B", label: "Heading two" }],
            questions: [{ n: 1, type: "MATCH_HEADINGS", prompt: "Paragraph 1" }],
        }],
        answerKey: [{ n: 1, answer: "A", explanation: "" }],
    });
    assert.equal(validate(match, { forPublish: true }).ok, true);

    match.questionGroups[0].options = [{ key: "A", label: "" }]; // too few / unlabelled
    assert.equal(validate(match, { forPublish: true }).ok, false);
});

test("validate: multiple-choice (multi) respects chooseCount", () => {
    const mm = baseSession({
        questionGroups: [{
            type: "MCMA", heading: "", instruction: "", chooseCount: 2,
            questions: [{ n: 1, type: "MCMA", text: "Pick two", options: [{ key: "A", label: "a" }, { key: "B", label: "b" }, { key: "C", label: "c" }] }],
        }],
        answerKey: [{ n: 1, answer: ["A", "C"], explanation: "" }],
    });
    assert.equal(validate(mm, { forPublish: true }).ok, true);
    mm.answerKey[0].answer = ["A"]; // only one, chooseCount=2
    assert.equal(validate(mm, { forPublish: true }).ok, false);
});

test("editor: round-trips an MCQ group through fromApi/toApi and validates", () => {
    const dom = new JSDOM("<!DOCTYPE html><body></body>");
    const ed = createEditor({ api: createStubApi(SEED), doc: dom.window.document, win: dom.window });
    const mcApi = baseSession({
        id: "mc", status: "draft",
        questionGroups: [{ type: "MCQ", heading: "Q1", instruction: "", questions: [{ n: 1, type: "MCQ", text: "Which?", options: [{ key: "A", label: "a" }, { key: "B", label: "b" }, { key: "C", label: "c" }] }] }],
        answerKey: [{ n: 1, answer: "B", explanation: "" }],
    });
    ed._fromApi(mcApi);
    const out = ed._toApi();
    assert.equal(out.questionGroups[0].type, "MCQ");
    assert.equal(out.questionGroups[0].questions[0].options.length, 3);
    assert.equal(out.answerKey[0].answer, "B");
    assert.equal(validate(out, { forPublish: true }).ok, true);
});
