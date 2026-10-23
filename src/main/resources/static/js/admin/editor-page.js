/* Entry for the admin session editor page. */
import { createEditor } from "./editor.js";
import { api } from "./api.js";
import { adminReady, setUnsavedCheck } from "./guard.js";

await adminReady; // signed in as ADMIN or AUTHOR, else this page never starts

const editor = createEditor({ api });
setUnsavedCheck(() => editor.isDirty());
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => editor.init());
} else {
    editor.init();
}
window.__adminEditor = editor;
