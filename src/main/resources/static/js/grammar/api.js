/* =========================================================================
   grammar/api.js — calls to /api/v1/grammar (grammar design §5.1).
   The access token is added by auth.js; guests call the same endpoints.
   ========================================================================= */

import { apiFetch } from "../auth.js";

const API = "/api/v1/grammar";

export const levels = () => apiFetch(API + "/levels");

export function course(level, { topic, page = 0, size = 50 } = {}) {
    const p = new URLSearchParams({ level, page: String(page), size: String(size) });
    if (topic) p.set("topic", topic);
    return apiFetch(API + "/course?" + p);
}

export const me = () => apiFetch(API + "/me");

export const chooseLevel = (level) => apiFetch(API + "/me/level", { method: "PUT", json: { level } });

export const lesson = (slug) => apiFetch(API + "/lessons/" + encodeURIComponent(slug));

export const startRun = (slug) => apiFetch(API + "/lessons/" + encodeURIComponent(slug) + "/runs", { method: "POST" });

export const answer = (runId, itemId, value) =>
    apiFetch(API + "/runs/" + encodeURIComponent(runId) + "/answers", { method: "POST", json: { itemId, answer: value } });

export const review = () => apiFetch(API + "/review");

export const answerCard = (cardId, value) =>
    apiFetch(API + "/review/" + encodeURIComponent(cardId) + "/answer", { method: "POST", json: { answer: value } });

/** The address of a lesson page. */
export const lessonUrl = (slug) => "/grammar/lessons/" + encodeURIComponent(slug);
