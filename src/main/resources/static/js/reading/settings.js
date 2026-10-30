/* =========================================================================
   reading/settings.js — the "Aa" reading settings panel, the draggable
   divider between passage and questions, and the one-time "new" tip.
   Every change is applied at once and saved (reading/prefs.js).
   ========================================================================= */

const TIP_KEY = "bandup.reader.tipSeen";

function remembered(key) {
    try {
        return localStorage.getItem(key);
    } catch (e) {
        return null;
    }
}

function remember(key, value) {
    try {
        localStorage.setItem(key, value);
    } catch (e) {
        /* fine: just not remembered */
    }
}

export function initReaderSettings(doc = document) {
    const P = window.BandUpReaderPrefs;
    const root = doc.getElementById("reading-root");
    const button = doc.getElementById("readerBtn");
    const panel = doc.getElementById("readerPanel");
    if (!P || !root || !button || !panel) return;

    let prefs = P.load();

    function render() {
        panel.querySelectorAll("output[data-for]").forEach((out) => {
            out.textContent = Math.round(prefs[out.dataset.for] * 100) + "%";
        });
        panel.querySelectorAll("button[data-step]").forEach((b) => {
            const value = prefs[b.dataset.step];
            b.disabled = Number(b.dataset.dir) < 0 ? value <= P.SCALE.min : value >= P.SCALE.max;
        });
        panel.querySelectorAll(".seg[data-pref] button").forEach((b) => {
            b.setAttribute("aria-pressed", String(prefs[b.closest(".seg").dataset.pref] === b.dataset.value));
        });
        const splitter = doc.getElementById("splitter");
        if (splitter) splitter.setAttribute("aria-valuenow", String(prefs.split));
    }

    function update(change, persist = true) {
        prefs = P.clean({ ...prefs, ...change });
        P.apply(root, prefs);
        if (persist) P.save(prefs);
        render();
    }

    // ---- Panel: open / close ----
    function setOpen(open) {
        panel.hidden = !open;
        button.setAttribute("aria-expanded", String(open));
        if (open) {
            hideTip();
            const first = panel.querySelector("button:not(:disabled)");
            if (first) first.focus();
        }
    }
    button.addEventListener("click", () => setOpen(panel.hidden));
    doc.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && !panel.hidden) {
            setOpen(false);
            button.focus();
        }
    });
    doc.addEventListener("click", (e) => {
        if (!panel.hidden && !panel.contains(e.target) && !button.contains(e.target)) setOpen(false);
    });
    const close = doc.getElementById("readerClose");
    if (close) close.addEventListener("click", () => { setOpen(false); button.focus(); });

    // ---- Controls ----
    panel.addEventListener("click", (e) => {
        const step = e.target.closest("button[data-step]");
        if (step) {
            const key = step.dataset.step;
            update({ [key]: prefs[key] + Number(step.dataset.dir) * P.SCALE.step });
            return;
        }
        const choice = e.target.closest(".seg[data-pref] button");
        if (choice) update({ [choice.closest(".seg").dataset.pref]: choice.dataset.value });
    });
    const reset = doc.getElementById("readerReset");
    if (reset) reset.addEventListener("click", () => update({ ...P.DEFAULTS }));

    // ---- Divider: drag (mouse/touch) or arrow keys ----
    const splitter = doc.getElementById("splitter");
    const main = doc.querySelector(".reading-main");
    if (splitter && main) {
        const percentAt = (clientX) => {
            const box = main.getBoundingClientRect();
            const fromLeft = ((clientX - box.left) / box.width) * 100;
            return prefs.layout === "swap" ? 100 - fromLeft : fromLeft;
        };
        let dragging = false;
        splitter.addEventListener("pointerdown", (e) => {
            dragging = true;
            splitter.setPointerCapture(e.pointerId);
            root.classList.add("is-resizing");
            e.preventDefault();
        });
        splitter.addEventListener("pointermove", (e) => {
            if (dragging) update({ split: percentAt(e.clientX) }, false);
        });
        const stop = () => {
            if (!dragging) return;
            dragging = false;
            root.classList.remove("is-resizing");
            P.save(prefs);
        };
        splitter.addEventListener("pointerup", stop);
        splitter.addEventListener("pointercancel", stop);
        splitter.addEventListener("dblclick", () => update({ split: P.DEFAULTS.split }));
        splitter.addEventListener("keydown", (e) => {
            const towardPassage = prefs.layout === "swap" ? "ArrowRight" : "ArrowLeft";
            const towardQuestions = prefs.layout === "swap" ? "ArrowLeft" : "ArrowRight";
            if (e.key === towardPassage) update({ split: prefs.split - 2 });
            else if (e.key === towardQuestions) update({ split: prefs.split + 2 });
            else if (e.key === "Home") update({ split: P.SPLIT.min });
            else if (e.key === "End") update({ split: P.SPLIT.max });
            else return;
            e.preventDefault();
        });
    }

    // ---- One-time tip about the new settings ----
    const tip = doc.getElementById("readerTip");
    function hideTip() {
        if (tip && !tip.hidden) tip.hidden = true;
        remember(TIP_KEY, "1");
    }
    if (tip && !remembered(TIP_KEY)) {
        tip.hidden = false;
        const show = doc.getElementById("readerTipOpen");
        const dismiss = doc.getElementById("readerTipClose");
        if (show) show.addEventListener("click", (e) => { e.stopPropagation(); setOpen(true); });
        if (dismiss) dismiss.addEventListener("click", hideTip);
    }

    render();
}
