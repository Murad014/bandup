/* =========================================================================
   account.js — the pages behind the account emails (requirement 12):
   /verify-email?token=…, /forgot-password, /reset-password?token=….
   ========================================================================= */

import { forgotPassword, resetPassword, verifyEmail } from "./auth.js";
import { busy, showFormError } from "./auth-forms.js";

const token = new URLSearchParams(location.search).get("token") || "";
const BAD_LINK = "This link is invalid or has expired.";

async function confirmPage(box) {
    const show = (icon, title, message) => {
        box.querySelector("[data-icon]").textContent = icon;
        box.querySelector("[data-title]").textContent = title;
        box.querySelector("[data-message]").textContent = message;
    };
    try {
        await verifyEmail(token);
        show("✅", "Email confirmed", "Your account is ready. Sign in to start practising.");
        box.querySelector("[data-sign-in]").hidden = false;
    } catch (e) {
        show("⚠️", "This link doesn't work", e.status === 400
            ? BAD_LINK + " Links work once, for 24 hours."
            : "Couldn't confirm right now. Please try again.");
        box.querySelector("[data-again]").hidden = false;
    }
}

function forgotPage(form) {
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        showFormError(form, "");
        const email = form.email.value.trim();
        if (!email) {
            showFormError(form, "Enter your email.");
            return;
        }
        busy(form, true);
        try {
            await forgotPassword(email);
            const done = document.querySelector("[data-forgot-done]");
            done.querySelector("[data-sent-to]").textContent = email;
            form.hidden = true;
            done.hidden = false;
        } catch (e) {
            busy(form, false);
            showFormError(form, "Couldn't send the link right now. Please try again.");
        }
    });
}

function resetPage(form) {
    if (!token) showFormError(form, BAD_LINK + " Ask for a new one on the sign-in page.");
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        showFormError(form, "");
        const field = form.querySelector('[data-field="password"]');
        field.classList.remove("has-error");
        form.querySelector('[data-error-for="password"]').textContent = "";
        busy(form, true);
        try {
            await resetPassword(token, form.password.value);
            form.hidden = true;
            document.querySelector("[data-reset-done]").hidden = false;
        } catch (e) {
            busy(form, false);
            if (e.status === 422 && e.errors.length) {
                field.classList.add("has-error");
                form.querySelector('[data-error-for="password"]').textContent = e.errors[0].message;
            } else if (e.status === 400) {
                showFormError(form, BAD_LINK + " Reset links work once, for 1 hour. Ask for a new one.");
            } else {
                showFormError(form, "Couldn't save the password right now. Please try again.");
            }
        }
    });
}

const verify = document.querySelector("[data-verify-email]");
const forgot = document.querySelector("[data-forgot-form]");
const reset = document.querySelector("[data-reset-form]");
if (verify) confirmPage(verify);
if (forgot) forgotPage(forgot);
if (reset) resetPage(reset);
