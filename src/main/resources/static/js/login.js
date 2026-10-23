/* The sign-in form. Every failed sign-in gets the same message (the API doesn't say which part was wrong). */

import { login } from "./auth.js";
import { busy, keepNext, nextPage, showFormError } from "./auth-forms.js";

const form = document.querySelector("[data-login-form]");
keepNext(form);

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    showFormError(form, "");
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
        showFormError(form, e.status === 401 ? "Wrong email or password."
                : "Couldn't sign in right now. Please try again.");
        form.password.value = "";
        form.password.focus();
    }
});
