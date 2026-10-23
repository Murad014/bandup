/* Entry for the admin sessions list page. */
import { createSessionsPage } from "./sessions.js";
import { api } from "./api.js";
import { adminReady } from "./guard.js";

await adminReady; // signed in as ADMIN or AUTHOR, else this page never starts

const page = createSessionsPage({ api });
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => page.init());
} else {
    page.init();
}
