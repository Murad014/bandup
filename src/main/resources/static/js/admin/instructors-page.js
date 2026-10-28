/* =========================================================================
   instructors-page.js — /admin/instructors: pending Instructor sign-ups,
   with Approve (AUTHOR role + email) and Reject. ADMIN only.
   ========================================================================= */

import { adminReady } from "./guard.js";
import { apiFetch, hasRole } from "../auth.js";

const BASE = "/api/v1/admin/instructor-requests";
const $ = (id) => document.getElementById(id);

function status(text) {
    $("instructorsStatus").textContent = text;
    $("instructorsStatus").hidden = !text;
}

function cell(text) {
    const td = document.createElement("td");
    td.textContent = text;
    return td;
}

function row(request) {
    const tr = document.createElement("tr");
    tr.dataset.userId = request.userId;
    tr.append(cell(request.displayName || "—"), cell(request.email),
        cell(request.emailConfirmed ? "Yes" : "Not yet"),
        cell(new Date(request.requestedAt).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })));
    const actions = document.createElement("td");
    const act = (label, cls, path, confirmText) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = cls;
        b.textContent = label;
        b.addEventListener("click", async () => {
            if (confirmText && !window.confirm(confirmText)) return;
            actions.querySelectorAll("button").forEach((x) => (x.disabled = true));
            try {
                await apiFetch(BASE + "/" + encodeURIComponent(request.userId) + path, { method: "POST" });
                tr.remove();
                if (!$("instructorsBody").children.length) empty();
            } catch (e) {
                actions.querySelectorAll("button").forEach((x) => (x.disabled = false));
                status(e.status === 409 ? "Someone already handled this request. Reload the page." : "Couldn't save. Please try again.");
            }
        });
        actions.appendChild(b);
    };
    act("Approve", "btn btn--primary", "/approve",
        "Approve " + (request.displayName || request.email) + "? They'll be able to create and publish passages.");
    act("Reject", "btn btn--danger", "/reject", null);
    tr.appendChild(actions);
    return tr;
}

function empty() {
    $("instructorsTable").hidden = true;
    status("No requests waiting.");
}

async function start() {
    const user = await adminReady;
    if (!hasRole(user, "ADMIN")) {
        status("Only an administrator can handle Instructor requests.");
        return;
    }
    try {
        const requests = await apiFetch(BASE);
        if (!requests.length) {
            empty();
            return;
        }
        requests.forEach((r) => $("instructorsBody").appendChild(row(r)));
        $("instructorsTable").hidden = false;
        status("");
    } catch (e) {
        status("Couldn't load the requests. Please try again.");
    }
}

start();
