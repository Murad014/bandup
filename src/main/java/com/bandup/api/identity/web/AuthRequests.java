package com.bandup.api.identity.web;

import lombok.experimental.UtilityClass;

/** JSON bodies accepted by {@link AuthController}. */
@UtilityClass
final class AuthRequests {

    record Register(String email, String password, String displayName, String uiLanguage, String supportLanguage,
            String accountType) {

        @Override
        public String toString() {
            return "Register[email=" + email + "]";
        }
    }

    /** "Send the link again" / "Forgot password". */
    record EmailOnly(String email) {
    }

    /** A link's secret (from the emailed URL). */
    record Token(String token) {
    }

    /** A new password from the reset link. */
    record Reset(String token, String password) {

        @Override
        public String toString() {
            return "Reset[password=…]";
        }
    }

    record Login(String email, String password) {

        @Override
        public String toString() {
            return "Login[email=" + email + "]";
        }
    }
}
