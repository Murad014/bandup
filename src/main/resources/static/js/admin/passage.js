/* =========================================================================
   passage.js — the passage editor: marks glossed words (span.w[data-g]) and
   evidence (span.ev[id]) on a contenteditable element, with a live preview
   of what the server will keep. The server sanitizes again; this is only so
   authors see the result.

   Gloss keys g1, g2… are allocated here and never renumbered (backend
   requirement 5.4): a new mark gets the highest key in the passage + 1.

   Authors don't need a precise selection: clicking in a word is enough for
   a gloss, clicking in a sentence is enough for evidence. The last caret or
   selection in the passage is remembered, so pressing a toolbar button
   (which may take the focus) still marks the right text.
   ========================================================================= */

const ALLOWED_TAGS = new Set(["P", "SPAN", "MARK", "EM", "STRONG", "B", "I", "BR"]);
const READING_AIDS = new Set(["w", "ev"]);

export function createPassageEditor(options = {}) {
    const d = options.doc || globalThis.document;
    const w = options.win || globalThis.window;
    const editEl = options.editEl;
    const previewEl = options.previewEl || null;
    let onChange = typeof options.onChange === "function" ? options.onChange : null;
    const onMarkClick = typeof options.onMarkClick === "function" ? options.onMarkClick : null;
    let lastRange = null;

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

    /** The selection or caret in the passage now, else the last one it had (or null). */
    function passageRange() {
        const s = w.getSelection ? w.getSelection() : null;
        if (s && s.rangeCount) {
            const r = s.getRangeAt(0);
            if (editEl.contains(r.commonAncestorContainer)) return r.cloneRange();
        }
        return lastRange && editEl.contains(lastRange.commonAncestorContainer) ? lastRange.cloneRange() : null;
    }

    function rememberSelection() {
        const s = w.getSelection ? w.getSelection() : null;
        if (!s || !s.rangeCount) return;
        const r = s.getRangeAt(0);
        if (editEl.contains(r.commonAncestorContainer)) lastRange = r.cloneRange();
    }

    /** The mark (".w" / ".ev") the range starts in, if any. */
    function markAt(range, className) {
        const node = range.startContainer;
        const el = node.nodeType === 1 ? node : node.parentNode;
        const mark = el && el.closest ? el.closest("." + className) : null;
        return mark && editEl.contains(mark) ? mark : null;
    }

    /** A collapsed caret grows to the word around it; a selection loses its outer spaces. */
    function wordRange(range) {
        if (range.collapsed) {
            const n = range.startContainer;
            if (n.nodeType !== 3) return null;
            const text = n.data;
            let a = range.startOffset;
            let b = range.startOffset;
            const isWord = (c) => /[\p{L}\p{N}'’-]/u.test(c);
            while (a > 0 && isWord(text[a - 1])) a--;
            while (b < text.length && isWord(text[b])) b++;
            if (a === b) return null;
            range.setStart(n, a);
            range.setEnd(n, b);
            return range;
        }
        return trimmed(range);
    }

    /** A collapsed caret grows to its sentence (within the same text); a selection loses its outer spaces. */
    function sentenceRange(range) {
        if (range.collapsed) {
            const n = range.startContainer;
            if (n.nodeType !== 3) return null;
            const text = n.data;
            let a = range.startOffset;
            let b = range.startOffset;
            while (a > 0 && !/[.!?]/.test(text[a - 1])) a--;
            while (b < text.length && !/[.!?]/.test(text[b])) b++;
            if (b < text.length) b++; // keep the full stop
            range.setStart(n, a);
            range.setEnd(n, b);
        }
        return trimmed(range);
    }

    function trimmed(range) {
        const s = range.startContainer;
        const e = range.endContainer;
        if (s.nodeType === 3) {
            let i = range.startOffset;
            while (i < s.data.length && /\s/.test(s.data[i]) && !(s === e && i >= range.endOffset)) i++;
            range.setStart(s, i);
        }
        if (e.nodeType === 3) {
            let j = range.endOffset;
            while (j > 0 && /\s/.test(e.data[j - 1]) && !(s === e && j <= range.startOffset)) j--;
            range.setEnd(e, j);
        }
        return range.collapsed ? null : range;
    }

    /**
     * Glosses the word at the caret (or the selected words).
     * @returns {{key, created}} — the existing key when the caret is already in a gloss; null with nothing to mark
     */
    function addGloss() {
        const r = passageRange();
        if (!r) return null;
        const existing = markAt(r, "w");
        if (existing) return { key: existing.getAttribute("data-g"), created: false };
        const range = wordRange(r);
        if (!range) return null;
        const key = wrap(range, "w", "data-g", nextGlossKey());
        lastRange = null;
        return { key, created: true };
    }

    /**
     * Marks the sentence at the caret (or the selection) as evidence.
     * @returns {{id, created}} — the existing id when the caret is already in evidence; null with nothing to mark
     */
    function markEvidence() {
        const r = passageRange();
        if (!r) return null;
        const existing = markAt(r, "ev");
        if (existing) return { id: existing.id, created: false };
        const range = sentenceRange(r);
        if (!range) return null;
        const id = wrap(range, "ev", "id", nextEvidenceId());
        lastRange = null;
        return { id, created: true };
    }

    /** Every glossed word with this key (a key may repeat). */
    function glossElements(key) {
        return [...editEl.querySelectorAll(".w[data-g]")].filter((el) => el.getAttribute("data-g") === key);
    }

    function evidenceElement(id) {
        return [...editEl.querySelectorAll(".ev[id]")].find((el) => el.id === id) || null;
    }

    /** Evidence in document order: [{id, text}]. */
    function evidence() {
        return [...editEl.querySelectorAll(".ev[id]")].map((el) => ({ id: el.id, text: el.textContent.trim() }));
    }

    /** Removes every mark of this gloss key (the words stay). */
    function removeGloss(key) {
        glossElements(key).forEach((el) => unwrap(el));
    }

    function removeMarkAtSelection() {
        const r = passageRange();
        if (!r) return false;
        const node = r.commonAncestorContainer;
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
        d.addEventListener("selectionchange", rememberSelection);
        editEl.addEventListener("click", (e) => {
            const mark = e.target && e.target.closest ? e.target.closest(".w[data-g]") : null;
            if (mark && onMarkClick && editEl.contains(mark)) onMarkClick(mark.getAttribute("data-g"), mark);
        });
    }

    return {
        setHtml, serialize, addGloss, markEvidence, removeMarkAtSelection, unwrap,
        glossMarks, evidenceIds, nextGlossKey, nextEvidenceId, glossElements, evidenceElement, evidence, removeGloss,
        setOnChange: (cb) => { onChange = typeof cb === "function" ? cb : null; },
    };
}
