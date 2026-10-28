/* The sign-in form. Every failed sign-in gets the same message (the API doesn't say which part was wrong). */

import { login, resendVerification } from "./auth.js";
import { busy, keepNext, nextPage, showFormError } from "./auth-forms.js";

const form = document.querySelector("[data-login-form]");
keepNext(form);

const confirmBox = form.querySelector("[data-confirm-email]");
confirmBox.querySelector("[data-resend]").addEventListener("click", async () => {
    try {
        await resendVerification(form.email.value.trim());
    } finally {
        confirmBox.querySelector("[data-resent]").hidden = false;
    }
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    showFormError(form, "");
    confirmBox.hidden = true;
    const email = form.email.value.trim();
    const password = form.password.value;
    if (!email || !password) {
        showFormError(form, "Enter your email and password.");
        return;
    }
    busy(form, true);
    try {
        await login(email, password);
        location.assign(nextPage());
    } catch (e) {
        busy(form, false);
        if (e.status === 403 && e.problem && e.problem.code === "EMAIL_NOT_VERIFIED") {
            confirmBox.hidden = false;
            confirmBox.querySelector("[data-resent]").hidden = true;
            return;
        }
        showFormError(form, e.status === 401 ? "Wrong email or password."
                : e.status === 429 ? e.message // "Too many sign-in attempts. Try again in N minutes."
                : "Couldn't sign in right now. Please try again.");
        form.password.value = "";
        form.password.focus();
    }
});
