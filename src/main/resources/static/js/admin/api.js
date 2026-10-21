/* =========================================================================
   api.js — admin REST API client.
     - stubApi: in-memory CRUD seeded from fixtures (dev/tests).
     - createHttpApi: talks to /api/v1/admin/** once the backend exists.
   Admin GET returns full objects (with answers); the learner read API never
   does. Flip USE_STUB to false to switch over.
   ========================================================================= */

import { SEED } from "./fixtures.js";

const USE_STUB = true;
const LATENCY_MS = 0;

function delay(ms) { return new Promise((r) => setTimeout(r, ms)); }
function clone(v) { return typeof structuredClone === "function" ? structuredClone(v) : JSON.parse(JSON.stringify(v)); }
function notFound(id) { const e = new Error("Not found: " + id); e.status = 404; return e; }

function slugify(title) {
    return String(title || "")
        .toLowerCase().trim()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "") || "session";
}

function summary(s) {
    return {
        id: s.id,
        title: s.title,
        stageLabel: s.stageLabel,
        status: s.status,
        questionCount: (s.questionGroups || []).reduce((n, g) => n + (g.questions ? g.questions.length : 0), 0),
    };
}

export function createStubApi(seed = SEED) {
    // Fresh in-memory store per instance (tests get isolation).
    const store = new Map(clone(seed).map((s) => [s.id, s]));

    function uniqueId(base) {
        let id = base, i = 2;
        while (store.has(id)) id = base + "-" + i++;
        return id;
    }

    return {
        async list() {
            await delay(LATENCY_MS);
            return [...store.values()].map(summary);
        },
        async get(id) {
            await delay(LATENCY_MS);
            if (!store.has(id)) throw notFound(id);
            return clone(store.get(id));
        },
        async create(session) {
            await delay(LATENCY_MS);
            const copy = clone(session);
            copy.id = uniqueId(slugify(copy.title));
            copy.status = copy.status || "draft";
            store.set(copy.id, copy);
            return clone(copy);
        },
        async update(id, session) {
            await delay(LATENCY_MS);
            if (!store.has(id)) throw notFound(id);
            const copy = clone(session);
            copy.id = id;
            copy.status = store.get(id).status; // status changes go through publish/unpublish
            store.set(id, copy);
            return clone(copy);
        },
        async remove(id) {
            await delay(LATENCY_MS);
            if (!store.has(id)) throw notFound(id);
            store.delete(id);
        },
        async publish(id) {
            await delay(LATENCY_MS);
            if (!store.has(id)) throw notFound(id);
            store.get(id).status = "published";
            return clone(store.get(id));
        },
        async unpublish(id) {
            await delay(LATENCY_MS);
            if (!store.has(id)) throw notFound(id);
            store.get(id).status = "draft";
            return clone(store.get(id));
        },
    };
}

export function createHttpApi(base = "/api/v1/admin") {
    async function json(res) {
        if (!res.ok) { const e = new Error("Request failed (" + res.status + ")"); e.status = res.status; try { e.body = await res.json(); } catch (_) {} throw e; }
        return res.status === 204 ? null : res.json();
    }
    const H = { "Content-Type": "application/json", Accept: "application/json" };
    return {
        list: async () => json(await fetch(base + "/passages", { headers: { Accept: "application/json" } })),
        get: async (id) => json(await fetch(base + "/passages/" + encodeURIComponent(id), { headers: { Accept: "application/json" } })),
        create: async (s) => json(await fetch(base + "/passages", { method: "POST", headers: H, body: JSON.stringify(s) })),
        update: async (id, s) => json(await fetch(base + "/passages/" + encodeURIComponent(id), { method: "PUT", headers: H, body: JSON.stringify(s) })),
        remove: async (id) => json(await fetch(base + "/passages/" + encodeURIComponent(id), { method: "DELETE", headers: H })),
        publish: async (id) => json(await fetch(base + "/passages/" + encodeURIComponent(id) + "/publish", { method: "POST", headers: H })),
        unpublish: async (id) => json(await fetch(base + "/passages/" + encodeURIComponent(id) + "/unpublish", { method: "POST", headers: H })),
    };
}

export const api = USE_STUB ? createStubApi() : createHttpApi();
