/* =========================================================================
   sessions.js — the admin list of exercises (admin API, backend §8.4).
   Loads api.list() once, then filters/searches/sorts in the browser.
   Edit, preview (published), publish/unpublish and delete; an exercise with
   learner attempts is archived instead of deleted and can't be edited.
   ========================================================================= */

export function createSessionsPage(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const api = options.api;

    const els = {};
    const state = { sessions: [], query: "", status: "all", sort: "title" };

    function byId(id) { return d.getElementById(id); }

    function cache() {
        els.root = byId("sessionsRoot");
        els.status = byId("sessionsStatus");
        els.table = byId("sessionsTable");
        els.tbody = byId("sessionsBody");
        els.empty = byId("sessionsEmpty");
        els.note = byId("sessionsNote");          // optional: "no matching results"
        els.count = byId("sessionsCount");         // optional
        els.search = byId("sessionsSearch");       // optional
        els.filter = byId("sessionsFilter");       // optional
        els.sortSel = byId("sessionsSort");        // optional
    }

    async function init() {
        cache();
        if (!els.root) return;
        if (els.tbody) els.tbody.addEventListener("click", onAction);
        if (els.search) els.search.addEventListener("input", () => { state.query = els.search.value; render(); });
        if (els.filter) els.filter.addEventListener("change", () => { state.status = els.filter.value; render(); });
        if (els.sortSel) els.sortSel.addEventListener("change", () => { state.sort = els.sortSel.value; render(); });
        await load();
    }

    async function load() {
        showStatus("Loading exercises…");
        try {
            state.sessions = await api.list();
            render();
        } catch (e) {
            showError();
        }
    }

    function showStatus(text) {
        if (els.status) { els.status.textContent = text; els.status.hidden = false; els.status.className = "admin-status"; }
        if (els.table) els.table.hidden = true;
        if (els.empty) els.empty.hidden = true;
        if (els.note) els.note.hidden = true;
    }

    function showError() {
        if (!els.status) return;
        els.status.hidden = false;
        els.status.className = "admin-error";
        els.status.textContent = "Couldn't load exercises. ";
        const retry = d.createElement("button");
        retry.type = "button"; retry.className = "btn"; retry.id = "sessionsRetry"; retry.textContent = "Retry";
        retry.addEventListener("click", () => load());
        els.status.appendChild(retry);
    }

    function visibleSessions() {
        const q = state.query.trim().toLowerCase();
        let rows = state.sessions.filter((s) => {
            if (state.status !== "all" && s.status !== state.status) return false;
            if (!q) return true;
            return (s.title || "").toLowerCase().includes(q) || stageOf(s).toLowerCase().includes(q)
                || (s.id || "").includes(q);
        });
        const by = state.sort;
        rows = rows.slice().sort((a, b) => {
            if (by === "status") return String(a.status).localeCompare(String(b.status));
            if (by === "questions") return (b.questionCount || 0) - (a.questionCount || 0);
            return String(a.title).localeCompare(String(b.title));
        });
        return rows;
    }

    function render() {
        if (els.status) els.status.hidden = true;

        // Nothing authored at all.
        if (state.sessions.length === 0) {
            if (els.table) els.table.hidden = true;
            if (els.note) els.note.hidden = true;
            if (els.empty) els.empty.hidden = false;
            if (els.count) els.count.textContent = "";
            return;
        }
        if (els.empty) els.empty.hidden = true;

        const rows = visibleSessions();
        if (els.count) els.count.textContent = rows.length + " of " + state.sessions.length;

        if (rows.length === 0) {
            if (els.table) els.table.hidden = true;
            if (els.note) { els.note.hidden = false; els.note.className = "admin-status"; els.note.textContent = "No exercises match your search."; }
            return;
        }
        if (els.note) els.note.hidden = true;
        if (els.table) els.table.hidden = false;
        els.tbody.textContent = "";
        rows.forEach((s) => els.tbody.appendChild(row(s)));
    }

    function stageOf(s) {
        return [s.stageTitle, s.position ? "Passage " + s.position : null].filter(Boolean).join(" · ");
    }

    function updatedOf(s) {
        if (!s.updatedAt) return "";
        const at = new Date(s.updatedAt);
        return Number.isNaN(at.getTime()) ? "" : at.toLocaleString();
    }

    function row(s) {
        const tr = d.createElement("tr");
        tr.dataset.id = s.id;
        tr.appendChild(cell(s.title));
        tr.appendChild(cell(stageOf(s)));

        const statusTd = d.createElement("td");
        const badge = d.createElement("span");
        const status = String(s.status || "DRAFT").toLowerCase();
        badge.className = "badge badge--" + status;
        badge.textContent = status;
        statusTd.appendChild(badge);
        if (s.revision > 1) statusTd.appendChild(d.createTextNode(" r" + s.revision));
        tr.appendChild(statusTd);

        tr.appendChild(cell(String(s.questionCount)));
        tr.appendChild(cell(updatedOf(s)));

        const actions = d.createElement("td");
        actions.className = "row-actions";
        if (status === "archived") {
            actions.appendChild(d.createTextNode("Kept for learners' attempts"));
            tr.appendChild(actions);
            return tr;
        }

        const edit = d.createElement("a");
        edit.className = "btn";
        edit.href = "/admin/sessions/" + encodeURIComponent(s.id) + "/edit";
        edit.textContent = "Edit";
        edit.setAttribute("data-admin-nav-link", "");
        actions.appendChild(edit);

        if (status === "published") {
            const preview = d.createElement("a");
            preview.className = "btn btn--text";
            preview.href = "/reading/" + encodeURIComponent(s.id);
            preview.target = "_blank";
            preview.rel = "noopener";
            preview.textContent = "Preview";
            actions.appendChild(preview);
        }

        const pub = d.createElement("button");
        pub.type = "button";
        pub.className = "btn btn--text";
        pub.dataset.action = status === "published" ? "unpublish" : "publish";
        pub.textContent = status === "published" ? "Unpublish" : "Publish";
        actions.appendChild(pub);

        const del = d.createElement("button");
        del.type = "button";
        del.className = "btn btn--danger";
        del.dataset.action = "delete";
        del.textContent = "Delete";
        actions.appendChild(del);

        tr.appendChild(actions);
        return tr;
    }

    function cell(text) {
        const td = d.createElement("td");
        td.textContent = text == null ? "" : text;
        return td;
    }

    async function onAction(e) {
        const btn = e.target.closest ? e.target.closest("button[data-action]") : null;
        if (!btn) return;
        const tr = btn.closest("tr");
        const id = tr && tr.dataset.id;
        if (!id) return;
        const s = state.sessions.find((x) => x.id === id);
        const action = btn.dataset.action;
        try {
            if (action === "delete") {
                if (!w.confirm('Delete "' + (s ? s.title : id) + '"? If learners have attempts on it, it will be archived instead.')) return;
                await api.remove(id);
            } else if (action === "publish") {
                await api.publish(id);
            } else if (action === "unpublish") {
                await api.unpublish(id);
            }
            await load();
            const after = state.sessions.find((x) => x.id === id);
            if (action === "delete" && after) {
                note('"' + after.title + '" was archived instead of deleted: learners have attempts on it.');
            }
        } catch (err) {
            if (err && err.status === 422 && err.errors && err.errors.length) {
                note("Can't publish yet — " + err.errors.map((e) => e.message).join(" ") + " Open it in the editor to fix this.");
            } else {
                showError();
            }
        }
    }

    function note(text) {
        if (!els.note) return;
        els.note.hidden = false;
        els.note.className = "admin-notice";
        els.note.textContent = text;
    }

    return { init, load, render, _state: state };
}
