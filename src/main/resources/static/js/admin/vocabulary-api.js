/* =========================================================================
   vocabulary-api.js — the admin word card API (vocabulary design §5.2)
   through auth.js. ADMIN or AUTHOR token; errors are ApiError.
   ========================================================================= */

import { apiFetch } from "../auth.js";

const BASE = "/api/v1/admin/vocabulary/words";

export const vocabularyApi = {
    /** {cards: [{id, word, level, status, source, saves, lastError, updatedAt}], total} */
    list: ({ status, q, page = 0, size = 50 } = {}) => {
        const p = new URLSearchParams({ page: String(page), size: String(size) });
        if (status && status !== "all") p.set("status", status);
        if (q) p.set("q", q);
        return apiFetch(BASE + "?" + p);
    },
    get: (id) => apiFetch(BASE + "/" + id),
    /** body = the card as GET returned it, changed, with its version; stale → 409. */
    update: (id, body) => apiFetch(BASE + "/" + id, { method: "PUT", json: body }),
    regenerate: (id) => apiFetch(BASE + "/" + id + "/regenerate", { method: "POST" }),
    /** 204; 409 when learners saved the word. */
    remove: (id) => apiFetch(BASE + "/" + id, { method: "DELETE" }),
};
