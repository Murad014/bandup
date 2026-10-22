package com.bandup.api.identity.web;

/** JSON bodies accepted by {@link AuthController}. */
final class AuthRequests {

    private AuthRequests() {
    }

    record Register(String email, String password, String displayName, String uiLanguage, String supportLanguage) {

        @Override
        public String toString() {
            return "Register[email=" + email + "]";
        }
    }

    record Login(String email, String password) {

        @Override
        public String toString() {
            return "Login[email=" + email + "]";
        }
    }
}
