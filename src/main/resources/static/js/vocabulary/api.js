/* =========================================================================
   vocabulary/api.js — calls to /api/v1/vocabulary (vocabulary design §5.1).
   The access token is added by auth.js. Errors are ApiError.
   ========================================================================= */

import { apiFetch } from "../auth.js";

const API = "/api/v1/vocabulary";

/** A word's card if Orbiko's word bank has it (public; never calls the AI). */
export const lookup = (text) => apiFetch(API + "/lookup?text=" + encodeURIComponent(text));

/** source: {exercise, sentence} or null → {word, alreadySaved}. */
export const save = (text, source) => apiFetch(API + "/words", { method: "POST", json: { text, source: source || null } });

export function words({ state, q, exercise, page = 0, size = 50 } = {}) {
    const p = new URLSearchParams({ page: String(page), size: String(size) });
    if (state && state !== "all") p.set("state", state);
    if (q) p.set("q", q);
    if (exercise) p.set("exercise", exercise);
    return apiFetch(API + "/words?" + p);
}

export const wordsByIds = (ids) => apiFetch(API + "/words?ids=" + ids.map(encodeURIComponent).join(","));

export const remove = (id) => apiFetch(API + "/words/" + encodeURIComponent(id), { method: "DELETE" });

export const retry = (id) => apiFetch(API + "/words/" + encodeURIComponent(id) + "/retry", { method: "POST" });

export const startSession = (kind, exercise) =>
    apiFetch(API + "/sessions", { method: "POST", json: { kind, exercise: exercise || null } });

export const answer = (sessionId, taskId, value) =>
    apiFetch(API + "/sessions/" + encodeURIComponent(sessionId) + "/answers", { method: "POST", json: { taskId, answer: value } });

/** Tells the navbar badge to count new words again. */
export function changed() {
    document.dispatchEvent(new CustomEvent("bandup:words-changed"));
}

/** The browser reads a word aloud (British English); quietly does nothing without speech. */
export function say(text) {
    try {
        const speech = window.speechSynthesis;
        if (!speech || !text) return;
        const u = new SpeechSynthesisUtterance(text);
        u.lang = "en-GB";
        u.rate = 0.9;
        speech.cancel();
        speech.speak(u);
    } catch (e) {
        /* no speech on this device */
    }
}

export function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}
