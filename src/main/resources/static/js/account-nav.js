/* =========================================================================
   account-nav.js — the navbar's account part, from auth.js:
   "Sign in" when signed out; when signed in, "My progress" and an account
   button (avatar + name) that opens a menu with "My progress" and "Sign out".
   There is no Admin link: admins open /admin themselves (user decision).
   ========================================================================= */

import { init, onChange, logout } from "./auth.js";

const signIn = document.querySelector("[data-nav-signin]");
const account = document.querySelector("[data-nav-account]");
const name = document.querySelector("[data-nav-username]");
const signOut = document.querySelector("[data-nav-signout]");
const progress = document.querySelector("[data-nav-progress]");
const toggle = document.querySelector("[data-account-toggle]");
const menu = document.getElementById("accountMenu");

const ROLE_NAMES = { ADMIN: "Admin", AUTHOR: "Author", TRANSLATOR: "Translator", INSTRUCTOR: "Instructor" };

function roleOf(user) {
    const roles = user.roles || [];
    const known = Object.keys(ROLE_NAMES).find((r) => roles.includes(r));
    return known ? ROLE_NAMES[known] : "Learner";
}
const signInLink = signIn && signIn.querySelector("a");

if (signInLink) {
    const here = location.pathname + location.search;
    const onAuthPage = location.pathname === "/login" || location.pathname === "/register";
    signInLink.href = onAuthPage ? "/login" : "/login?next=" + encodeURIComponent(here);
}

function render(user) {
    if (signIn) signIn.hidden = !!user;
    if (account) account.hidden = !user;
    if (progress) progress.hidden = !user;
    const display = user ? user.displayName || "Account" : "";
    if (name) name.textContent = display;
    document.querySelectorAll("[data-nav-avatar]").forEach((a) => { a.textContent = display.trim().charAt(0); });
    document.querySelectorAll("[data-nav-fullname]").forEach((n) => { n.textContent = display; });
    document.querySelectorAll("[data-nav-role]").forEach((r) => { r.textContent = user ? roleOf(user) : ""; });
    if (!user) setMenu(false);
}

function setMenu(open) {
    if (!menu || !toggle) return;
    menu.hidden = !open;
    toggle.setAttribute("aria-expanded", String(open));
}

if (toggle && menu) {
    toggle.addEventListener("click", () => {
        setMenu(menu.hidden);
        if (!menu.hidden) {
            const first = menu.querySelector("[role=menuitem]");
            if (first) first.focus();
        }
    });
    document.addEventListener("click", (e) => {
        if (!menu.hidden && !menu.contains(e.target) && !toggle.contains(e.target)) setMenu(false);
    });
    document.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && !menu.hidden) {
            setMenu(false);
            toggle.focus();
        }
    });
}

if (signOut) {
    signOut.addEventListener("click", async () => {
        await logout();
        if (location.pathname.startsWith("/admin")) location.assign("/");
    });
}

onChange(render);
init(); // ends in signed in or signed out; both call render
