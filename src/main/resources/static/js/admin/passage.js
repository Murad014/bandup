/* =========================================================================
   passage.js — passage editor: gloss/evidence helpers, serialization, and a
   live preview. Operates on a contenteditable element. The serialized output
   keeps only allowed markup (authoring convenience); the SERVER re-sanitizes.
   ========================================================================= */

const ALLOWED_TAGS = new Set(["P", "SPAN", "MARK", "EM", "STRONG", "B", "I", "BR"]);
const ALLOWED_ATTRS = new Set(["class", "id", "data-az"]);

export function createPassageEditor(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const editEl = options.editEl;
    const previewEl = options.previewEl || null;
    let onChange = typeof options.onChange === "function" ? options.onChange : null;

    function setHtml(html) {
        editEl.innerHTML = html || "";
        renderPreview();
    }

    function nextEvidenceId() {
        let max = 0;
        editEl.querySelectorAll(".ev[id]").forEach((el) => {
            const m = /^e(\d+)$/.exec(el.id);
            if (m) max = Math.max(max, Number(m[1]));
        });
        return "e" + (max + 1);
    }

    function evidenceIds() {
        return [...editEl.querySelectorAll(".ev[id]")].map((el) => el.id);
    }

    function applyGloss(range, az) {
        const span = d.createElement("span");
        span.className = "w";
        span.setAttribute("data-az", az || "");
        span.appendChild(range.extractContents());
        range.insertNode(span);
        changed();
        return span;
    }

    function applyEvidence(range) {
        const span = d.createElement("span");
        span.className = "ev";
        span.id = nextEvidenceId();
        span.appendChild(range.extractContents());
        range.insertNode(span);
        changed();
        return span.id;
    }

    function unwrap(el) {
        const parent = el.parentNode;
        while (el.firstChild) parent.insertBefore(el.firstChild, el);
        el.remove();
        parent.normalize();
        changed();
    }

    function currentRange() {
        const s = w.getSelection ? w.getSelection() : null;
        if (!s || !s.rangeCount) return null;
        const r = s.getRangeAt(0);
        if (r.collapsed) return null;
        if (!editEl.contains(r.commonAncestorContainer)) return null;
        return r;
    }

    // Toolbar actions (use the live selection).
    function addGloss(az) { const r = currentRange(); if (!r) return false; applyGloss(r, az); return true; }
    function markEvidence() { const r = currentRange(); if (!r) return null; return applyEvidence(r); }
    function removeMarkAtSelection() {
        const s = w.getSelection ? w.getSelection() : null;
        if (!s || !s.rangeCount) return false;
        let node = s.getRangeAt(0).commonAncestorContainer;
        const el = node.nodeType === 1 ? node : node.parentNode;
        const mark = el && el.closest ? el.closest(".w, .ev") : null;
        if (mark && editEl.contains(mark)) { unwrap(mark); return true; }
        return false;
    }

    function serialize() {
        const tmp = d.createElement("div");
        tmp.innerHTML = editEl.innerHTML;
        cleanChildren(tmp);
        return tmp.innerHTML.trim();
    }

    function cleanChildren(parent) {
        [...parent.childNodes].forEach((n) => {
            if (n.nodeType === 3) return; // text
            if (n.nodeType !== 1) { n.remove(); return; } // comments etc.
            cleanChildren(n);
            if (!ALLOWED_TAGS.has(n.tagName)) {
                while (n.firstChild) parent.insertBefore(n.firstChild, n);
                n.remove();
                return;
            }
            [...n.attributes].forEach((a) => { if (!ALLOWED_ATTRS.has(a.name)) n.removeAttribute(a.name); });
        });
    }

    function renderPreview() {
        if (previewEl) previewEl.innerHTML = serialize();
    }

    function changed() {
        renderPreview();
        if (onChange) onChange(serialize());
    }

    function setOnChange(cb) { onChange = typeof cb === "function" ? cb : null; }

    // Keep preview/model in sync with free typing in the editable region.
    if (editEl && editEl.addEventListener) {
        editEl.addEventListener("input", () => changed());
    }

    return {
        setHtml, serialize, renderPreview,
        addGloss, markEvidence, removeMarkAtSelection,
        applyGloss, applyEvidence, unwrap,
        evidenceIds, nextEvidenceId, setOnChange,
    };
}
