/* =========================================================================
   account-nav.js — the navbar's account part, from auth.js:
   "Sign in" when signed out; the user's name + "Sign out" when signed in.
   There is no Admin link: admins open /admin themselves (user decision).
   ========================================================================= */

import { init, onChange, logout } from "./auth.js";

const signIn = document.querySelector("[data-nav-signin]");
const account = document.querySelector("[data-nav-account]");
const name = document.querySelector("[data-nav-username]");
const signOut = document.querySelector("[data-nav-signout]");
const signInLink = signIn && signIn.querySelector("a");

if (signInLink) {
    const here = location.pathname + location.search;
    const onAuthPage = location.pathname === "/login" || location.pathname === "/register";
    signInLink.href = onAuthPage ? "/login" : "/login?next=" + encodeURIComponent(here);
}

function render(user) {
    if (signIn) signIn.hidden = !!user;
    if (account) account.hidden = !user;
    if (name) name.textContent = user ? user.displayName || "Account" : "";
}

if (signOut) {
    signOut.addEventListener("click", async () => {
        await logout();
        if (location.pathname.startsWith("/admin")) location.assign("/");
    });
}

onChange(render);
init(); // ends in signed in or signed out; both call render
