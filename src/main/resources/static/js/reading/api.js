/* =========================================================================
   api.js — the UI's view of the REST API.

   Two implementations behind one interface:
     - stubApi:  resolves from local fixtures; grades in-process. Lets the UI
                 be built/tested before the backend exists.
     - httpApi:  talks to the real endpoints (GET /api/v1/passages/{id},
                 POST /api/v1/attempts) once the backend is available.

   Flip USE_STUB to false (or set it from the page) to switch over. Either way
   the GET response never includes answers — grading only happens on submit.
   ========================================================================= */

import { gradeAttempt } from "./core.js";
import { SESSIONS } from "./fixtures.js";

const USE_STUB = true;
const STUB_LATENCY_MS = 0;

function delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function clone(value) {
    return typeof structuredClone === "function"
        ? structuredClone(value)
        : JSON.parse(JSON.stringify(value));
}

function notFound(id) {
    const err = new Error("Session not found: " + id);
    err.status = 404;
    return err;
}

export const stubApi = {
    /** GET /api/v1/passages/{id} — public session only (no answers). */
    async fetchSession(id) {
        await delay(STUB_LATENCY_MS);
        const entry = SESSIONS[id];
        if (!entry) throw notFound(id);
        return clone(entry.session);
    },

    /** POST /api/v1/attempts — grade server-side and return results. */
    async submitAttempt(payload) {
        await delay(STUB_LATENCY_MS);
        const entry = SESSIONS[payload.passageId];
        if (!entry) throw notFound(payload.passageId);
        const graded = gradeAttempt(entry.answerKey, payload.answers || []);
        return { ...graded, elapsedSeconds: payload.elapsedSeconds };
    },
};

export function createHttpApi(base = "/api/v1") {
    return {
        async fetchSession(id) {
            const res = await fetch(base + "/passages/" + encodeURIComponent(id), {
                headers: { Accept: "application/json" },
            });
            if (!res.ok) {
                const err = new Error("Failed to load session (" + res.status + ")");
                err.status = res.status;
                throw err;
            }
            return res.json();
        },
        async submitAttempt(payload) {
            const res = await fetch(base + "/attempts", {
                method: "POST",
                headers: { "Content-Type": "application/json", Accept: "application/json" },
                body: JSON.stringify(payload),
            });
            if (!res.ok) {
                const err = new Error("Failed to submit attempt (" + res.status + ")");
                err.status = res.status;
                throw err;
            }
            return res.json();
        },
    };
}

export const api = USE_STUB ? stubApi : createHttpApi();
