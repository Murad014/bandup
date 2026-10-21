/* Entry for the admin sessions list page. */
import { createSessionsPage } from "./sessions.js";
import { api } from "./api.js";

const page = createSessionsPage({ api });
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => page.init());
} else {
    page.init();
}
