/* =========================================================================
   reading/prefs.js — the learner's reading settings (text size, layout,
   page width, line spacing, font, colours, cover), kept in localStorage.

   Loaded as a normal (blocking) script at the top of the reading page, so the
   saved layout is applied before the page is drawn: no jump after a refresh.
   reading/settings.js (the settings panel) uses window.BandUpReaderPrefs.
   ========================================================================= */
(function () {
    "use strict";

    var KEY = "bandup.reader.v1";
    var DEFAULTS = {
        passageScale: 1,
        questionScale: 1,
        split: 50,          // passage width in %, side-by-side layouts
        layout: "side",     // side | swap (passage right) | stack
        width: "normal",    // normal | wide | full
        spacing: "normal",  // compact | normal | relaxed
        font: "sans",       // sans | serif | easy
        tone: "auto",       // auto (site theme) | sepia | dark
        cover: "show"       // show | hide
    };
    var CHOICES = {
        layout: ["side", "swap", "stack"],
        width: ["normal", "wide", "full"],
        spacing: ["compact", "normal", "relaxed"],
        font: ["sans", "serif", "easy"],
        tone: ["auto", "sepia", "dark"],
        cover: ["show", "hide"]
    };
    var SCALE = { min: 0.85, max: 1.6, step: 0.05 };
    var SPLIT = { min: 30, max: 70 };
    var EASY_FONT = "https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&display=swap";

    function clamp(n, lo, hi) { return Math.min(hi, Math.max(lo, n)); }
    function round2(n) { return Math.round(n * 100) / 100; }

    /** Only known values; anything else falls back to the default. */
    function clean(raw) {
        var p = {};
        Object.keys(DEFAULTS).forEach(function (k) { p[k] = DEFAULTS[k]; });
        if (!raw || typeof raw !== "object") return p;
        Object.keys(CHOICES).forEach(function (k) {
            if (CHOICES[k].indexOf(raw[k]) >= 0) p[k] = raw[k];
        });
        ["passageScale", "questionScale"].forEach(function (k) {
            if (typeof raw[k] === "number" && isFinite(raw[k])) p[k] = round2(clamp(raw[k], SCALE.min, SCALE.max));
        });
        if (typeof raw.split === "number" && isFinite(raw.split)) p.split = Math.round(clamp(raw.split, SPLIT.min, SPLIT.max));
        return p;
    }

    function load() {
        try {
            return clean(JSON.parse(localStorage.getItem(KEY) || "null"));
        } catch (e) {
            return clean(null); // storage blocked or broken: defaults
        }
    }

    function save(p) {
        try {
            localStorage.setItem(KEY, JSON.stringify(p));
        } catch (e) {
            /* not remembered, still applied */
        }
    }

    function loadEasyFont() {
        if (document.getElementById("easyFont")) return;
        var link = document.createElement("link");
        link.id = "easyFont";
        link.rel = "stylesheet";
        link.href = EASY_FONT;
        document.head.appendChild(link);
    }

    function apply(root, p) {
        Object.keys(CHOICES).forEach(function (k) { root.setAttribute("data-" + k, p[k]); });
        root.style.setProperty("--passage-scale", String(p.passageScale));
        root.style.setProperty("--question-scale", String(p.questionScale));
        root.style.setProperty("--split", p.split + "%");
        if (p.font === "easy") loadEasyFont();
    }

    var root = document.currentScript && document.currentScript.parentElement;
    if (root) apply(root, load());

    window.BandUpReaderPrefs = {
        KEY: KEY, DEFAULTS: DEFAULTS, CHOICES: CHOICES, SCALE: SCALE, SPLIT: SPLIT,
        clean: clean, load: load, save: save, apply: apply
    };
})();
