/*
 * The sign-up form. The API checks everything and reports every field at once
 * (422); each message is shown under its field and the input is kept.
 * Registering doesn't sign in: the account works once the emailed link is
 * opened, so a successful sign-up shows "check your email".
 */

import { register, resendVerification } from "./auth.js";
import { busy, keepNext, showFormError } from "./auth-forms.js";

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

function showCheckEmail(email, instructor) {
    const done = document.querySelector("[data-check-email]");
    done.querySelector("[data-sent-to]").textContent = email;
    done.querySelector("[data-instructor-note]").hidden = !instructor;
    form.hidden = true;
    done.hidden = false;
    done.querySelector("[data-resend]").addEventListener("click", async () => {
        try {
            await resendVerification(email);
        } finally {
            done.querySelector("[data-resent]").hidden = false;
        }
    });
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearErrors();
    const email = form.email.value.trim();
    const password = form.password.value;
    const language = form.supportLanguage.value;
    busy(form, true);
    try {
        const accountType = form.accountType.value || "LEARNER";
        await register({
            email,
            password,
            displayName: form.displayName.value.trim(),
            uiLanguage: language,
            supportLanguage: language,
            accountType,
        });
        showCheckEmail(email, accountType === "INSTRUCTOR");
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
