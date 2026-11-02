/* =========================================================================
   grammar-page.js — /admin/grammar, the list of grammar lessons (grammar
   requirement 12, design §6.5). Loads the list once, then filters and
   searches in the browser. Edit, open (published), publish at a position or
   at the end, unpublish, delete (archived instead when learners used it).
   ========================================================================= */

import { grammarApi as api } from "./grammar-api.js";
import { adminReady } from "./guard.js";

await adminReady;

const $ = (id) => document.getElementById(id);
const LEVELS = ["A2", "B1", "B2", "C1"];
const state = { lessons: [], query: "", level: "all", status: "all" };

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

async function load() {
    $("grammarStatusLine").hidden = false;
    $("grammarStatusLine").className = "admin-status";
    $("grammarStatusLine").textContent = "Loading lessons…";
    try {
        state.lessons = await api.list();
    } catch (e) {
        $("grammarStatusLine").className = "admin-error";
        $("grammarStatusLine").textContent = "Couldn't load the lessons. ";
        const retry = el("button", "btn", "Retry");
        retry.type = "button";
        retry.addEventListener("click", load);
        $("grammarStatusLine").append(retry);
        $("grammarTable").hidden = true;
        return;
    }
    $("grammarStatusLine").hidden = true;
    render();
}

function visible() {
    const q = state.query.trim().toLowerCase();
    return state.lessons
        .filter((l) => (state.level === "all" || l.level === state.level)
            && (state.status === "all" || l.status === state.status)
            && (!q || [l.title, l.topic, l.id].some((v) => String(v || "").toLowerCase().includes(q))))
        .sort((a, b) => LEVELS.indexOf(a.level) - LEVELS.indexOf(b.level)
            || (a.position ?? Infinity) - (b.position ?? Infinity)
            || String(a.title).localeCompare(String(b.title)));
}

function render() {
    const empty = state.lessons.length === 0;
    $("grammarEmpty").hidden = !empty;
    const rows = visible();
    $("grammarCount").textContent = empty ? "" : rows.length + " of " + state.lessons.length;
    $("grammarTable").hidden = empty;
    $("grammarBody").replaceChildren(...rows.map(row));
    if (!empty && rows.length === 0) {
        const tr = el("tr");
        const td = el("td", "admin-status", "No lessons match.");
        td.colSpan = 8;
        tr.append(td);
        $("grammarBody").append(tr);
    }
}

function row(l) {
    const tr = el("tr");
    tr.dataset.id = l.id;
    const status = String(l.status).toLowerCase();
    const badge = el("span", "badge badge--" + status, status);
    const statusCell = el("td");
    statusCell.append(badge);
    if (l.revision > 1) statusCell.append(" r" + l.revision);
    const updated = l.updatedAt ? new Date(l.updatedAt).toLocaleString() : "";
    tr.append(el("td", null, l.level), el("td", null, l.position ?? "—"), el("td", null, l.title),
        el("td", null, l.topic), statusCell, el("td", null, String(l.checkCount)), el("td", null, updated));

    const actions = el("td", "row-actions");
    const edit = el("a", "btn", status === "archived" ? "View" : "Edit");
    edit.href = "/admin/grammar/" + encodeURIComponent(l.id) + "/edit";
    edit.setAttribute("data-admin-nav-link", "");
    actions.append(edit);
    if (status === "published") {
        const open = el("a", "btn btn--text", "Open");
        open.href = "/grammar/lessons/" + encodeURIComponent(l.id);
        open.target = "_blank";
        open.rel = "noopener";
        actions.append(open);
    }
    if (status !== "archived") {
        actions.append(action(status === "published" ? "unpublish" : "publish",
            status === "published" ? "Unpublish" : "Publish", "btn btn--text"));
        actions.append(action("delete", "Delete", "btn btn--danger"));
    }
    tr.append(actions);
    return tr;
}

function action(name, label, className) {
    const b = el("button", className, label);
    b.type = "button";
    b.dataset.action = name;
    return b;
}

function note(text, error) {
    $("grammarNote").hidden = false;
    $("grammarNote").className = error ? "admin-error" : "admin-notice";
    $("grammarNote").textContent = text;
}

async function onAction(e) {
    const button = e.target.closest("button[data-action]");
    if (!button) return;
    const id = button.closest("tr").dataset.id;
    const lesson = state.lessons.find((l) => l.id === id);
    $("grammarNote").hidden = true;
    try {
        if (button.dataset.action === "publish") {
            const answer = window.prompt("Position in the " + lesson.level + " course (empty = at the end):", "");
            if (answer === null) return;
            const position = answer.trim() ? Number(answer.trim()) : null;
            if (position !== null && !(Number.isInteger(position) && position >= 1)) {
                note("The position must be a whole number from 1.", true);
                return;
            }
            await api.publish(id, position);
        } else if (button.dataset.action === "unpublish") {
            await api.unpublish(id);
        } else {
            if (!window.confirm("Delete “" + lesson.title + "”? If learners used it, it is archived instead.")) return;
            await api.remove(id);
        }
    } catch (err) {
        const list = err && err.errors && err.errors.length ? " " + err.errors.map((x) => x.field + " — " + x.message).join("; ") : "";
        note((err && err.message ? err.message : "That didn't work.") + list, true);
        return;
    }
    await load();
    const after = state.lessons.find((l) => l.id === id);
    if (button.dataset.action === "delete" && after) {
        note("“" + after.title + "” was archived instead of deleted: learners have used it.");
    }
}

$("grammarBody").addEventListener("click", onAction);
$("grammarSearch").addEventListener("input", (e) => { state.query = e.target.value; render(); });
$("grammarLevel").addEventListener("change", (e) => { state.level = e.target.value; render(); });
$("grammarStatus").addEventListener("change", (e) => { state.status = e.target.value; render(); });
load();
