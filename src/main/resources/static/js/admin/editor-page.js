/* Entry for the admin session editor page. */
import { createEditor } from "./editor.js";
import { api } from "./api.js";

const editor = createEditor({ api });
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => editor.init());
} else {
    editor.init();
}
window.__adminEditor = editor;
