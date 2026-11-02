/* =========================================================================
   grammar-api.js — the admin grammar API (grammar design §5.2) through
   auth.js. ADMIN or AUTHOR token; errors are ApiError (status, problem,
   errors[{field, message}] for 400/422).
   ========================================================================= */

import { apiFetch } from "../auth.js";

const BASE = "/api/v1/admin/grammar/lessons";
const one = (slug) => BASE + "/" + encodeURIComponent(slug);

export const grammarApi = {
    /** [{id, level, topic, title, status, position, revision, checkCount, updatedAt}] */
    list: () => apiFetch(BASE),
    /** The lesson with its answer keys and state (AdminLessonView). */
    get: (slug) => apiFetch(one(slug)),
    /** body = the lesson format with "id"; 201 → AdminLessonView (DRAFT). */
    create: (body) => apiFetch(BASE, { method: "POST", json: body }),
    /** body = the lesson format with the loaded "version"; stale → 409. */
    update: (slug, body) => apiFetch(one(slug), { method: "PUT", json: body }),
    /** position empty → the end of the level's course; a taken position → 409. */
    publish: (slug, position) =>
        apiFetch(one(slug) + "/publish", { method: "POST", json: position ? { position } : {} }),
    unpublish: (slug) => apiFetch(one(slug) + "/unpublish", { method: "POST" }),
    /** 204; archived instead when learners used the lesson. */
    remove: (slug) => apiFetch(one(slug), { method: "DELETE" }),
};
