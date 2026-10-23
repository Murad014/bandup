/*
 * The sign-up form. The API checks everything and reports every field at once
 * (422); each message is shown under its field and the input is kept.
 * Registering doesn't sign in, so a successful sign-up signs in right after.
 */

import { login, register } from "./auth.js";
import { busy, keepNext, nextPage, showFormError } from "./auth-forms.js";

const form = document.querySelector("[data-register-form]");
keepNext(form);

const browserLanguage = (navigator.language || "").toLowerCase().split("-")[0];
if ([...form.supportLanguage.options].some((o) => o.value === browserLanguage)) {
    form.supportLanguage.value = browserLanguage;
}

function clearErrors() {
    showFormError(form, "");
    form.querySelectorAll("[data-field]").forEach((f) => f.classList.remove("has-error"));
    form.querySelectorAll("[data-error-for]").forEach((e) => (e.textContent = ""));
}

function showFieldErrors(errors) {
    const unplaced = [];
    errors.forEach(({ field, message }) => {
        const slot = form.querySelector('[data-error-for="' + CSS.escape(field) + '"]');
        if (slot) {
            slot.textContent = message;
            slot.closest("[data-field]").classList.add("has-error");
        } else {
            unplaced.push(message);
        }
    });
    if (unplaced.length) showFormError(form, unplaced.join(" "));
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearErrors();
    const email = form.email.value.trim();
    const password = form.password.value;
    const language = form.supportLanguage.value;
    busy(form, true);
    try {
        await register({
            email,
            password,
            displayName: form.displayName.value.trim(),
            uiLanguage: language,
            supportLanguage: language,
        });
        await login(email, password);
        location.assign(nextPage());
    } catch (e) {
        busy(form, false);
        if (e.status === 422 && e.errors.length) {
            showFieldErrors(e.errors);
        } else if (e.status === 409) {
            showFieldErrors([{ field: "email", message: "This email already has an account. Sign in instead." }]);
        } else {
            showFormError(form, "Couldn't create the account right now. Please try again.");
        }
    }
});
