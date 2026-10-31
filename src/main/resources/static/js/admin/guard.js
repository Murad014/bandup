/* =========================================================================
   guard.js — admin pages are for ADMIN and AUTHOR accounts (frontend task 4).
   Signed out → sign-in page (and back here afterwards); signed in without the
   role → a message instead of the admin UI. The admin content stays hidden
   until this check passes. It's a convenience only: the admin API checks the
   token on every request.

   Page scripts wait for it before calling the API:
       import { adminReady } from "./guard.js";
       await adminReady;
   ========================================================================= */

import { currentUser, hasRole, logout, onChange } from "../auth.js";

const NEVER = new Promise(() => {});
let hasUnsavedWork = () => false;

/** The editor tells the guard about unsaved work, so signing out elsewhere doesn't throw it away. */
export function setUnsavedCheck(fn) {
    hasUnsavedWork = typeof fn === "function" ? fn : () => false;
}

function goToSignIn() {
    location.replace("/login?next=" + encodeURIComponent(location.pathname + location.search));
}

function showNoAccess() {
    const main = document.querySelector(".admin-main");
    const box = document.createElement("section");
    box.className = "admin-noaccess";
    box.setAttribute("data-admin-noaccess", "");
    const title = document.createElement("h1");
    title.className = "admin-h1";
    title.textContent = "You don't have access";
    const text = document.createElement("p");
    text.className = "admin-sub";
    text.textContent = "The admin area is for authors and admins. Sign in with an author account, or go back to practising.";
    const back = document.createElement("a");
    back.className = "btn btn--primary";
    back.href = "/";
    back.textContent = "Back to Orbiko";
    box.append(title, text, back);
    main.replaceChildren(box);
}

function showAccount(user) {
    const account = document.querySelector("[data-admin-account]");
    if (!account) return;
    account.querySelector("[data-admin-username]").textContent = user.displayName || "Account";
    account.hidden = false;
    account.querySelector("[data-admin-signout]").addEventListener("click", async () => {
        await logout();
        location.assign("/");
    });
}

export const adminReady = (async () => {
    const user = await currentUser();
    if (!user) {
        goToSignIn();
        return NEVER;
    }
    if (!hasRole(user, "ADMIN", "AUTHOR")) {
        showNoAccess();
        document.body.classList.add("admin-ready");
        return NEVER;
    }
    showAccount(user);
    if (!hasRole(user, "ADMIN")) {
        // Authors don't handle Instructor requests (the API refuses them anyway).
        document.querySelectorAll('[data-admin-nav-link][data-key="instructors"]').forEach((a) => (a.hidden = true));
    }
    document.body.classList.add("admin-ready");
    onChange((now) => {
        // Signed out here or in another tab. With unsaved work, stay: the next save
        // gets a 401, and the editor keeps the work and offers it back after sign-in.
        if (!now && !hasUnsavedWork()) goToSignIn();
    });
    return user;
})();
