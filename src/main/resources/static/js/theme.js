/* =========================================================================
   theme.js — persisted light/dark theme.
   Applies the saved theme to <html data-theme> immediately (before paint) to
   avoid a flash, exposes window.BandUpTheme.toggle(), and wires any element
   with [data-theme-toggle]. Light unless the learner picked dark (BandUp is
   bright by default; the OS setting doesn't switch it).
   Loaded synchronously in <head>.
   ========================================================================= */
(function () {
    "use strict";
    var KEY = "bandup-theme";
    var root = document.documentElement;

    function stored() {
        try { return localStorage.getItem(KEY); } catch (e) { return null; }
    }
    function current() {
        return stored() === "dark" ? "dark" : "light";
    }
    function apply(t) {
        if (t === "light" || t === "dark") root.setAttribute("data-theme", t);
        else root.removeAttribute("data-theme");
    }

    // Apply immediately (only if an explicit choice exists).
    apply(stored());

    function syncButtons() {
        var dark = current() === "dark";
        document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
            btn.setAttribute("aria-pressed", dark ? "true" : "false");
            btn.title = dark ? "Switch to light mode" : "Switch to dark mode";
            var icon = btn.querySelector("[data-theme-icon]") || btn;
            icon.textContent = dark ? "☀" : "☾";
        });
    }

    window.BandUpTheme = {
        get: current,
        set: function (t) {
            try { localStorage.setItem(KEY, t); } catch (e) {}
            apply(t);
            syncButtons();
        },
        toggle: function () {
            window.BandUpTheme.set(current() === "dark" ? "light" : "dark");
        },
    };

    document.addEventListener("DOMContentLoaded", function () {
        syncButtons();
        document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
            btn.addEventListener("click", function () { window.BandUpTheme.toggle(); });
        });
    });
})();
