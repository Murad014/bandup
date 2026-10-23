/* =========================================================================
   api.js — the learner API (backend design §7.4), through auth.js:
   a signed-in learner's token is sent, so their attempts are saved for them;
   anonymous learners can practise too.
   Errors are ApiError (status + Problem Details).
   ========================================================================= */

import { apiFetch } from "../auth.js";

function query(lang) {
    return lang ? "?lang=" + encodeURIComponent(lang) : "";
}

export function createApi(base = "/api/v1") {
    return {
        /** GET /passages/{slug}?lang= — the passage and questions, never answers. */
        fetchSession(slug, lang) {
            return apiFetch(base + "/passages/" + encodeURIComponent(slug) + query(lang));
        },
        /** POST /attempts?lang= — graded on the server; explanations in `lang`. */
        submitAttempt(payload, lang) {
            return apiFetch(base + "/attempts" + query(lang), { method: "POST", json: payload });
        },
    };
}

export const api = createApi();
