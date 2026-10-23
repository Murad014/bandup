/* =========================================================================
   api.js — the admin API (backend design §8.4) through auth.js.
   Every call needs an ADMIN or AUTHOR token; errors are ApiError
   (status, Problem Details, errors[] for 400/422).
   ========================================================================= */

import { apiFetch } from "../auth.js";

const BASE = "/api/v1/admin/passages";
const one = (slug) => BASE + "/" + encodeURIComponent(slug);

export const api = {
    /** [{id, title, status, revision, stageTitle, position, questionCount, updatedAt}] */
    list: () => apiFetch(BASE),
    /** The full editable exercise (AdminExerciseView). */
    get: (slug) => apiFetch(one(slug)),
    /** body = ExerciseInput with "id" (the slug); 201 → AdminExerciseView. */
    create: (body) => apiFetch(BASE, { method: "POST", json: body }),
    /** body = ExerciseInput with the loaded "version"; stale → 409. */
    update: (slug, body) => apiFetch(one(slug), { method: "PUT", json: body }),
    publish: (slug) => apiFetch(one(slug) + "/publish", { method: "POST" }),
    unpublish: (slug) => apiFetch(one(slug) + "/unpublish", { method: "POST" }),
    /** 204; archived instead when learners have attempts. */
    remove: (slug) => apiFetch(one(slug), { method: "DELETE" }),
    /** A JPEG/PNG up to 3 MB → {id, url, width, height}; bad file → 422 (field "file"). */
    uploadImage: (file) => {
        const form = new FormData();
        form.append("file", file);
        return apiFetch("/api/v1/admin/media", { method: "POST", form });
    },
};
