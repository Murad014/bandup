/* =========================================================================
   sidebar.js — admin shell behaviour:
     1. Responsive off-canvas sidebar toggle (aria-expanded).
     2. A navigation guard so the editor can warn before leaving with unsaved
        changes. Pages register a guard:
            window.BandUpAdminNav.registerGuard(() => editor.isDirty());
        and clear it when saved:
            window.BandUpAdminNav.clearGuard();
   ========================================================================= */
(function () {
    "use strict";

    var shell = document.querySelector("[data-admin-shell]");
    var toggle = document.querySelector("[data-admin-sb-toggle]");
    if (shell && toggle) {
        toggle.addEventListener("click", function () {
            var open = shell.classList.toggle("sb-open");
            toggle.setAttribute("aria-expanded", open ? "true" : "false");
        });
    }

    var guard = null;
    window.BandUpAdminNav = {
        registerGuard: function (fn) { guard = (typeof fn === "function") ? fn : null; },
        clearGuard: function () { guard = null; }
    };

    function shouldBlock() {
        try { return guard !== null && guard() === true; } catch (e) { return false; }
    }

    document.querySelectorAll("a[data-admin-nav-link]").forEach(function (link) {
        link.addEventListener("click", function (e) {
            if (shouldBlock() &&
                !window.confirm("You have unsaved changes. Leave and lose them?")) {
                e.preventDefault();
            }
        });
    });

    window.addEventListener("beforeunload", function (e) {
        if (shouldBlock()) { e.preventDefault(); e.returnValue = ""; }
    });
})();
