/* =========================================================================
   reading.js — entry point of the reading page: wires the controller
   (reading/app.js) to the learner API and picks the help language:
   the learner's own choice on this browser, else their account's support
   language, else the browser's language, else the server's default.
   ========================================================================= */

import { createReadingApp } from "./reading/app.js";
import { api } from "./reading/api.js";
import { currentUser } from "./auth.js";
import { HELP_LANGUAGES } from "./reading/core.js";

const KEY = "bandup.helpLanguage";

function saved() {
    try {
        return localStorage.getItem(KEY);
    } catch (e) {
        return null; // storage blocked: just don't remember
    }
}

function remember(code) {
    try {
        localStorage.setItem(KEY, code);
    } catch (e) {
        /* not remembered, still applied */
    }
}

async function helpLanguage() {
    const offered = HELP_LANGUAGES.map((l) => l.code);
    const choice = saved();
    if (choice && offered.includes(choice)) return choice;
    const user = await currentUser();
    if (user && user.supportLanguage) return user.supportLanguage;
    const browser = (navigator.language || "").toLowerCase().split("-")[0];
    return offered.includes(browser) ? browser : null;
}

async function start() {
    const picker = document.getElementById("helpLang");
    if (picker && picker.options.length === 0) {
        HELP_LANGUAGES.forEach((l) => picker.add(new Option(l.name, l.code)));
    }
    const app = createReadingApp({ api, lang: await helpLanguage(), onLanguage: remember });
    window.__readingApp = app; // for debugging in the console
    await app.init();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
} else {
    start();
}
