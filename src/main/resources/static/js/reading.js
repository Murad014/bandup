/* =========================================================================
   reading.js — browser entry point for the Reading Practice page.
   Wires the DOM controller (app.js) to the API client (api.js, currently the
   stub) and initialises once the DOM is ready.
   ========================================================================= */

import { createReadingApp } from "./reading/app.js";
import { api } from "./reading/api.js";

const app = createReadingApp({ api });

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => app.init());
} else {
    app.init();
}

// Exposed for debugging in the browser console.
window.__readingApp = app;
