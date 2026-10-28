/* =========================================================================
   auth.js — the signed-in state in the browser (backend design §6.2).

   - The access token lives in this module's memory only: never in
     localStorage/sessionStorage/cookies, so injected scripts can't read it
     from storage and it's gone when the tab closes.
   - On page load one silent POST /api/v1/auth/refresh (the HttpOnly
     refresh cookie) restores the session.
   - Refreshes are single-flight: one per tab at a time, and one per browser
     at a time (Web Locks), so two tabs never present the same refresh token
     — the server would treat that as theft and end the session.
   - apiFetch() adds the Bearer token and, on a 401, refreshes once and
     retries once.

   Import it as an ES module; the browser runs it once per page, so every
   module that imports it shares the same state.
   ========================================================================= */

const AUTH = "/api/v1/auth";
const LOCK = "bandup-auth-refresh";
const channel = typeof BroadcastChannel === "function" ? new BroadcastChannel("bandup-auth") : null;

let accessToken = null;
let user = null;
let refreshTimer = null;
let inFlight = null;
let lastUpdate = 0;
const listeners = new Set();
let ready = null;

/** An error answered by the API, with its Problem Details body. */
export class ApiError extends Error {
    constructor(status, problem) {
        super((problem && (problem.detail || problem.title)) || "Request failed (" + status + ")");
        this.status = status;
        this.problem = problem || {};
        /** [{field, message}] for 400/422 answers, else []. */
        this.errors = Array.isArray(this.problem.errors) ? this.problem.errors : [];
    }
}

async function problemOf(res) {
    try {
        return await res.json();
    } catch (e) {
        return null;
    }
}

function setSignedIn(body) {
    accessToken = body.accessToken;
    user = body.user;
    lastUpdate = Date.now();
    scheduleRefresh(body.expiresIn);
    notify();
}

function setSignedOut() {
    accessToken = null;
    user = null;
    lastUpdate = Date.now();
    clearTimeout(refreshTimer);
    notify();
}

function notify() {
    listeners.forEach((fn) => {
        try {
            fn(user);
        } catch (e) {
            console.error(e);
        }
    });
}

/** Refresh a minute before the access token expires, so requests rarely meet a 401. */
function scheduleRefresh(expiresInSeconds) {
    clearTimeout(refreshTimer);
    const ms = Math.max(10, (Number(expiresInSeconds) || 900) - 60) * 1000;
    refreshTimer = setTimeout(() => refresh(), ms);
}

async function refreshNow(startedAt) {
    // Another tab refreshed while this one waited for the lock: its new token
    // arrived over the channel, so there's nothing to do.
    if (lastUpdate > startedAt && accessToken) {
        return true;
    }
    const res = await fetch(AUTH + "/refresh", { method: "POST", credentials: "same-origin" });
    if (res.ok) {
        const body = await res.json();
        setSignedIn(body);
        if (channel) channel.postMessage({ type: "signed-in", body });
        return true;
    }
    setSignedOut();
    return false;
}

/** Restores or renews the session from the refresh cookie. Resolves true when signed in. */
export function refresh() {
    if (!inFlight) {
        const startedAt = Date.now();
        const run = () => refreshNow(startedAt);
        inFlight = (navigator.locks ? navigator.locks.request(LOCK, run) : run())
            .catch(() => {
                setSignedOut();
                return false;
            })
            .finally(() => {
                inFlight = null;
            });
    }
    return inFlight;
}

/** Starts the silent sign-in once per page; later calls return the same promise. */
export function init() {
    if (!ready) ready = refresh();
    return ready;
}

/** The signed-in user ({id, displayName, roles, uiLanguage, supportLanguage, targetLanguage}) or null. */
export async function currentUser() {
    await init();
    return user;
}

export function hasRole(u, ...roles) {
    return !!u && Array.isArray(u.roles) && roles.some((r) => u.roles.includes(r));
}

/** Called with the user (or null) whenever the signed-in state changes, in this tab or another. */
export function onChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
}

/**
 * fetch() for /api/v1/** with the access token. JSON bodies: pass `json`.
 * Returns the parsed JSON (null for 204); throws ApiError for 4xx/5xx.
 */
/** json → a JSON body; form → a FormData body (multipart, e.g. an image upload). */
export async function apiFetch(url, { method = "GET", json, form, headers = {} } = {}) {
    await init();
    const send = () => {
        const h = { Accept: "application/json", ...headers };
        if (json !== undefined) h["Content-Type"] = "application/json";
        if (accessToken) h.Authorization = "Bearer " + accessToken;
        return fetch(url, {
            method,
            headers: h,
            credentials: "same-origin",
            body: form !== undefined ? form : json === undefined ? undefined : JSON.stringify(json),
        });
    };
    let res = await send();
    if (res.status === 401 && (await refresh())) {
        res = await send(); // once: a second 401 is final
    }
    if (!res.ok) throw new ApiError(res.status, await problemOf(res));
    return res.status === 204 ? null : res.json();
}

/** POST /auth/login; throws ApiError (401 for every wrong combination). */
export async function login(email, password) {
    const res = await fetch(AUTH + "/login", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ email, password }),
    });
    if (!res.ok) throw new ApiError(res.status, await problemOf(res));
    const body = await res.json();
    ready = Promise.resolve(true);
    setSignedIn(body);
    if (channel) channel.postMessage({ type: "signed-in", body });
    return body.user;
}

/** POST /auth/register (doesn't sign in); throws ApiError (422 with field errors, 409 taken email). */
export async function register(form) {
    const res = await fetch(AUTH + "/register", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(form),
    });
    if (!res.ok) throw new ApiError(res.status, await problemOf(res));
    return res.json();
}

/** POST to an account-email endpoint (no token); throws ApiError on failure. */
async function accountPost(path, body) {
    const res = await fetch(AUTH + path, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(body),
    });
    if (!res.ok) throw new ApiError(res.status, await problemOf(res));
}

/** Opens the emailed confirmation link (400 when it's invalid, used or expired). */
export const verifyEmail = (token) => accountPost("/verify-email", { token });
/** Sends the confirmation link again (always succeeds: reveals nothing). */
export const resendVerification = (email) => accountPost("/resend-verification", { email });
/** Emails a password reset link (always succeeds: reveals nothing). */
export const forgotPassword = (email) => accountPost("/forgot-password", { email });
/** Sets a new password from the reset link (400 bad link, 422 weak password). */
export const resetPassword = (token, password) => accountPost("/reset-password", { token, password });

/** Ends the whole session on the server and in every tab. */
export async function logout() {
    try {
        await fetch(AUTH + "/logout", { method: "POST", credentials: "same-origin" });
    } finally {
        setSignedOut();
        if (channel) channel.postMessage({ type: "signed-out" });
    }
}

/** Only same-site paths are allowed as a place to go back to ("/admin", not "//evil.com"). */
export function safeNext(value, fallback = "/") {
    if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
        return fallback;
    }
    return value;
}

if (channel) {
    channel.onmessage = (event) => {
        const msg = event.data || {};
        if (msg.type === "signed-in" && msg.body) setSignedIn(msg.body);
        if (msg.type === "signed-out") setSignedOut();
    };
}
