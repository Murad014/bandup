/* Shared bits of the sign-in and sign-up forms. */

import { safeNext } from "./auth.js";

/** Where to go after signing in: ?next= when it's a same-site path, else home. */
export function nextPage() {
    return safeNext(new URLSearchParams(location.search).get("next"), "/");
}

/** Keeps ?next= on the link to the other form. */
export function keepNext(form) {
    const next = new URLSearchParams(location.search).get("next");
    form.querySelectorAll("[data-keep-next]").forEach((a) => {
        if (next) a.href = a.getAttribute("href") + "?next=" + encodeURIComponent(safeNext(next));
    });
}

export function showFormError(form, message) {
    const box = form.querySelector("[data-form-error]");
    box.textContent = message || "";
    box.hidden = !message;
}

export function busy(form, on) {
    form.querySelectorAll("button, input, select").forEach((el) => (el.disabled = on));
}
