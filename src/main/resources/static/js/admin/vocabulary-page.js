/* =========================================================================
   vocabulary-page.js — /admin/vocabulary, the word cards (vocabulary
   requirement 8): search and status filter on the server, then Edit,
   Regenerate (ready or failed cards) and Delete (cards nobody saved).
   ========================================================================= */

import { vocabularyApi as api } from "./vocabulary-api.js";
import { adminReady } from "./guard.js";

await adminReady;

const $ = (id) => document.getElementById(id);
const SIZE = 50;
const state = { status: "all", q: "", page: 0, cards: [], total: 0 };

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

async function load(append = false) {
    $("vcStatusLine").hidden = false;
    $("vcStatusLine").className = "admin-status";
    $("vcStatusLine").textContent = "Loading word cards…";
    try {
        const page = await api.list({ status: state.status, q: state.q, page: state.page, size: SIZE });
        state.cards = append ? state.cards.concat(page.cards) : page.cards;
        state.total = page.total;
    } catch (e) {
        $("vcStatusLine").className = "admin-error";
        $("vcStatusLine").textContent = "Couldn't load the word cards. Reload the page to try again.";
        return;
    }
    $("vcStatusLine").hidden = true;
    render();
}

function render() {
    const none = state.cards.length === 0;
    $("vcEmpty").hidden = !none || state.q !== "" || state.status !== "all";
    $("vcTable").hidden = none;
    $("vcCount").textContent = state.cards.length + " of " + state.total;
    $("vcBody").replaceChildren(...state.cards.map(row));
    if (none && ($("vcEmpty").hidden)) {
        const tr = el("tr");
        const td = el("td", "admin-status", "No word cards match.");
        td.colSpan = 7;
        tr.append(td);
        $("vcBody").append(tr);
        $("vcTable").hidden = false;
    }
    $("vcMore").hidden = state.cards.length >= state.total;
}

function row(c) {
    const tr = el("tr");
    tr.dataset.id = String(c.id);
    const status = String(c.status).toLowerCase();
    const statusCell = el("td");
    statusCell.append(el("span", "badge badge--" + status, status));
    if (c.lastError && status !== "ready") statusCell.append(el("div", "vc-error-text", c.lastError));
    tr.append(el("td", null, c.word), el("td", null, c.level || "—"), statusCell,
        el("td", null, c.saves + (c.saves === 1 ? " learner" : " learners")), el("td", null, c.source === "ADMIN" ? "admin" : "AI"),
        el("td", null, c.updatedAt ? new Date(c.updatedAt).toLocaleString() : ""));
    const actions = el("td", "row-actions");
    const edit = el("a", "btn", "Edit");
    edit.href = "/admin/vocabulary/" + c.id + "/edit";
    edit.setAttribute("data-admin-nav-link", "");
    actions.append(edit);
    if (status === "ready" || status === "failed") actions.append(button("regenerate", "Regenerate", "btn btn--text"));
    if (c.saves === 0 && status !== "preparing") actions.append(button("delete", "Delete", "btn btn--danger"));
    tr.append(actions);
    return tr;
}

function button(action, label, className) {
    const b = el("button", className, label);
    b.type = "button";
    b.dataset.action = action;
    return b;
}

function note(text, error) {
    $("vcNote").hidden = !text;
    $("vcNote").className = error ? "admin-error" : "admin-notice";
    $("vcNote").textContent = text;
}

$("vcBody").addEventListener("click", async (e) => {
    const b = e.target.closest("button[data-action]");
    if (!b) return;
    const id = Number(b.closest("tr").dataset.id);
    const card = state.cards.find((c) => c.id === id);
    note("");
    try {
        if (b.dataset.action === "regenerate") {
            await api.regenerate(id);
            note("The AI is writing “" + card.word + "” again. Learners keep their word meanwhile.");
        } else {
            if (!window.confirm("Delete “" + card.word + "”?")) return;
            await api.remove(id);
        }
    } catch (err) {
        note(err && err.message ? err.message : "That didn't work.", true);
        return;
    }
    await load();
});

let typing = null;
$("vcSearch").addEventListener("input", (e) => {
    clearTimeout(typing);
    typing = setTimeout(() => {
        state.q = e.target.value.trim();
        state.page = 0;
        load();
    }, 250);
});
$("vcStatus").addEventListener("change", (e) => {
    state.status = e.target.value;
    state.page = 0;
    load();
});
$("vcMore").addEventListener("click", () => {
    state.page += 1;
    load(true);
});
load();
