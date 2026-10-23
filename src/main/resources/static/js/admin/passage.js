/* =========================================================================
   passage.js — the passage editor: marks glossed words (span.w[data-g]) and
   evidence (span.ev[id]) on a contenteditable element, with a live preview
   of what the server will keep. The server sanitizes again; this is only so
   authors see the result.

   Gloss keys g1, g2… are allocated here and never renumbered (backend
   requirement 5.4): a new mark gets the highest key in the passage + 1.
   ========================================================================= */

const ALLOWED_TAGS = new Set(["P", "SPAN", "MARK", "EM", "STRONG", "B", "I", "BR"]);
const READING_AIDS = new Set(["w", "ev"]);

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

    function nextKey(selector, attribute, prefix) {
        let max = 0;
        editEl.querySelectorAll(selector).forEach((el) => {
            const m = new RegExp("^" + prefix + "(\\d+)$").exec(el.getAttribute(attribute) || "");
            if (m) max = Math.max(max, Number(m[1]));
        });
        return prefix + (max + 1);
    }

    function nextGlossKey() {
        return nextKey(".w[data-g]", "data-g", "g");
    }

    function nextEvidenceId() {
        return nextKey(".ev[id]", "id", "e");
    }

    /** The glossed words in document order: [{key, surface}] (a key may repeat; the server reports that). */
    function glossMarks() {
        return [...editEl.querySelectorAll(".w[data-g]")].map((el) => ({ key: el.getAttribute("data-g"), surface: el.textContent.trim() }));
    }

    function evidenceIds() {
        return [...editEl.querySelectorAll(".ev[id]")].map((el) => el.id);
    }

    function wrap(range, className, attribute, value) {
        const span = d.createElement("span");
        span.className = className;
        span.setAttribute(attribute, value);
        span.appendChild(range.extractContents());
        range.insertNode(span);
        changed();
        return value;
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
        if (r.collapsed || !editEl.contains(r.commonAncestorContainer)) return null;
        return r;
    }

    /** Marks the selected word(s) as glossed; returns the new key, or null without a selection. */
    function addGloss() {
        const r = currentRange();
        return r ? wrap(r, "w", "data-g", nextGlossKey()) : null;
    }

    /** Marks the selection as evidence; returns the new id, or null. */
    function markEvidence() {
        const r = currentRange();
        return r ? wrap(r, "ev", "id", nextEvidenceId()) : null;
    }

    function removeMarkAtSelection() {
        const s = w.getSelection ? w.getSelection() : null;
        if (!s || !s.rangeCount) return false;
        const node = s.getRangeAt(0).commonAncestorContainer;
        const el = node.nodeType === 1 ? node : node.parentNode;
        const mark = el && el.closest ? el.closest(".w, .ev") : null;
        if (mark && editEl.contains(mark)) {
            unwrap(mark);
            return true;
        }
        return false;
    }

    /** The HTML as the server's passage sanitizer will keep it (allowed tags; class w/ev, data-g on .w, id on .ev). */
    function serialize() {
        const tmp = d.createElement("div");
        tmp.innerHTML = editEl.innerHTML;
        cleanChildren(tmp);
        return tmp.innerHTML.trim();
    }

    function cleanChildren(parent) {
        [...parent.childNodes].forEach((n) => {
            if (n.nodeType === 3) return;
            if (n.nodeType !== 1) { n.remove(); return; }
            cleanChildren(n);
            if (!ALLOWED_TAGS.has(n.tagName)) {
                while (n.firstChild) parent.insertBefore(n.firstChild, n);
                n.remove();
                return;
            }
            const classes = [...n.classList].filter((c) => READING_AIDS.has(c));
            const key = n.getAttribute("data-g");
            const id = n.getAttribute("id");
            [...n.attributes].forEach((a) => n.removeAttribute(a.name));
            if (n.tagName !== "SPAN" || !classes.length) return;
            n.className = classes.join(" ");
            if (classes.includes("w") && key) n.setAttribute("data-g", key);
            if (classes.includes("ev") && id) n.id = id;
        });
    }

    function renderPreview() {
        if (previewEl) previewEl.innerHTML = serialize();
    }

    function changed() {
        renderPreview();
        if (onChange) onChange(serialize());
    }

    if (editEl && editEl.addEventListener) {
        editEl.addEventListener("input", () => changed());
    }

    return {
        setHtml, serialize, addGloss, markEvidence, removeMarkAtSelection, unwrap,
        glossMarks, evidenceIds, nextGlossKey, nextEvidenceId,
        setOnChange: (cb) => { onChange = typeof cb === "function" ? cb : null; },
    };
}
