/* =========================================================================
   nav.js — shared app-shell behaviour:
     1. Responsive navbar toggle (hamburger) with aria-expanded state.
     2. A navigation guard hook so a feature (e.g. an in-progress Reading
        session) can warn the user before they navigate away via the navbar
        or close/refresh the tab.

   Feature pages opt in by registering a guard:
       window.BandUpNav.registerGuard(() => session.isInProgress());
   and clear it when safe:
       window.BandUpNav.clearGuard();
   With no guard registered (e.g. the home page), navigation is never blocked.
   ========================================================================= */
(function () {
    "use strict";

    var nav = document.querySelector("[data-app-nav]");

    /* ---- 1. Responsive toggle ---------------------------------------- */
    if (nav) {
        var toggle = nav.querySelector("[data-nav-toggle]");
        if (toggle) {
            toggle.addEventListener("click", function () {
                var open = nav.classList.toggle("open");
                toggle.setAttribute("aria-expanded", open ? "true" : "false");
            });
        }
    }

    /* ---- 2. Navigation guard ----------------------------------------- */
    var guard = null;

    window.BandUpNav = {
        registerGuard: function (fn) { guard = (typeof fn === "function") ? fn : null; },
        clearGuard: function () { guard = null; }
    };

    function shouldBlock() {
        try {
            return guard !== null && guard() === true;
        } catch (e) {
            return false;
        }
    }

    // Intercept navbar link clicks.
    if (nav) {
        nav.querySelectorAll("a[data-nav-link]").forEach(function (link) {
            link.addEventListener("click", function (e) {
                if (shouldBlock() &&
                    !window.confirm("You have an unsubmitted session. Leave and lose your progress?")) {
                    e.preventDefault();
                }
            });
        });
    }

    // Cover hard navigations / refresh / tab close.
    window.addEventListener("beforeunload", function (e) {
        if (shouldBlock()) {
            e.preventDefault();
            e.returnValue = "";
        }
    });
})();
