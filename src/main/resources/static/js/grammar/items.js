/* =========================================================================
   grammar/items.js — one grammar item on screen (grammar requirement 5):
   choose (options), type (fill the gap), fix (correct the sentence) and
   build (tiles in order), then the server's feedback. Used by the lesson
   and the review pages. The browser never knows the answer: `answer(value)`
   asks the server, which replies with {correct, expected, why, explanation}.
   ========================================================================= */

const TYPE_LABEL = { choose: "choose", type: "type it", fix: "fix it", build: "build it" };
const KEYS = ["A", "B", "C", "D"];
const ALLOWED = new Set(["B", "I", "BR"]);

export function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

/**
 * A lesson text with its allowed formatting (<b>, <i>, <br>) as DOM nodes;
 * anything else becomes plain text. "___" becomes a highlighted blank.
 */
export function rich(html, className) {
    const box = el("span", className);
    const doc = new DOMParser().parseFromString("<body>" + (html || ""), "text/html");
    copy(doc.body, box);
    return box;
}

function copy(from, to) {
    for (const node of from.childNodes) {
        if (node.nodeType === Node.TEXT_NODE) {
            appendWithBlanks(to, node.textContent);
        } else if (node.nodeType === Node.ELEMENT_NODE) {
            if (ALLOWED.has(node.tagName)) {
                const kept = document.createElement(node.tagName.toLowerCase());
                copy(node, kept);
                to.append(kept);
            } else {
                copy(node, to);
            }
        }
    }
}

function appendWithBlanks(to, text) {
    const parts = text.split("___");
    parts.forEach((part, i) => {
        if (i > 0) to.append(el("span", "g-blank", "____"));
        if (part) to.append(document.createTextNode(part));
    });
}

/**
 * Shows `item` in `box` and resolves once the learner has seen the feedback
 * and pressed the next button.
 *
 * @param opts.kick       the small label above the item ("✏️ Guided practice · 1 of 3")
 * @param opts.header     an optional node above the card (e.g. the mastery bar)
 * @param opts.nextLabel  the label of the button after the feedback
 * @param opts.answer     value → Promise of the server's feedback
 * @param opts.footer     feedback → an optional node below the explanation
 * @returns Promise<{correct, feedback}>
 */
export function showItem(box, item, opts) {
    return new Promise((resolve, reject) => {
        const card = el("div", "g-card g-q");
        card.dataset.item = item.id;
        card.dataset.type = item.type;
        const kick = el("div", "g-q__kick");
        kick.append(el("span", null, opts.kick), el("span", "g-q__type", TYPE_LABEL[item.type] || ""));
        card.append(kick, rich(item.prompt, "g-q__prompt"));
        if (item.type === "fix") card.append(el("div", "g-q__wrong", "❌ " + item.wrong));
        if (item.type === "type") card.append(rich(escapeText(item.sentence), "g-q__sentence"));
        const feedback = el("div", "g-fb");
        feedback.hidden = true;
        feedback.setAttribute("aria-live", "polite");

        let busy = false;
        const send = async (value, onResult) => {
            if (busy) return;
            busy = true;
            try {
                const result = await opts.answer(value);
                onResult(result);
                showFeedback(feedback, result, value, item, opts, resolve);
            } catch (e) {
                busy = false;
                reject(e);
            }
        };

        if (item.type === "choose") card.append(chooseInput(item, send));
        else if (item.type === "build") card.append(buildInput(item, send));
        else card.append(textInput(send));
        card.append(feedback);
        box.replaceChildren(...(opts.header ? [opts.header, card] : [card]));
        const first = card.querySelector("input, button");
        if (first && item.type !== "choose") first.focus();
    });
}

/** Plain text that may hold "___", made safe for rich(). */
function escapeText(text) {
    const div = document.createElement("div");
    div.textContent = text || "";
    return div.innerHTML;
}

function chooseInput(item, send) {
    const list = el("div", "g-choices");
    const buttons = item.options.map((option, i) => {
        const button = el("button", "g-choice");
        button.type = "button";
        button.dataset.index = String(i);
        if (item.options.length > 2) button.append(el("b", "g-choice__key", KEYS[i]));
        button.append(document.createTextNode(option));
        button.addEventListener("click", () => send(i, (result) => {
            buttons.forEach((b) => (b.disabled = true));
            button.classList.add(result.correct ? "is-right" : "is-wrong");
            const right = item.options.indexOf(result.expected);
            if (!result.correct && right >= 0) buttons[right].classList.add("is-right");
        }));
        return button;
    });
    list.append(...buttons);
    return list;
}

function textInput(send) {
    const wrap = el("div", "g-typed");
    const input = el("input", "g-input");
    input.type = "text";
    input.autocomplete = "off";
    input.setAttribute("autocapitalize", "none");
    input.spellcheck = false;
    input.placeholder = "Type your answer…";
    input.setAttribute("aria-label", "Your answer");
    const check = el("button", "btn btn--primary g-check", "Check");
    check.type = "button";
    const submit = () => {
        const value = input.value.trim();
        if (!value) {
            input.focus();
            return;
        }
        send(value, (result) => {
            input.disabled = true;
            input.classList.add(result.correct ? "is-right" : "is-wrong");
            check.hidden = true;
        });
    };
    check.addEventListener("click", submit);
    input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            submit();
        }
    });
    wrap.append(input, check);
    return wrap;
}

function buildInput(item, send) {
    const wrap = el("div", "g-build");
    const tray = el("div", "g-tray");
    tray.setAttribute("aria-label", "Your sentence");
    const bank = el("div", "g-bank");
    bank.setAttribute("aria-label", "Words to use");
    const check = el("button", "btn btn--primary g-check", "Check");
    check.type = "button";
    const placed = []; // tile positions in item.tiles, in order
    let locked = false;

    const paint = () => {
        bank.replaceChildren(...item.tiles.map((word, i) => {
            const tile = el("button", "g-tile" + (placed.includes(i) ? " is-used" : ""), word);
            tile.type = "button";
            tile.disabled = locked || placed.includes(i);
            tile.addEventListener("click", () => {
                placed.push(i);
                paint();
            });
            return tile;
        }));
        tray.replaceChildren(...placed.map((i, at) => {
            const tile = el("button", "g-tile g-tile--placed", item.tiles[i]);
            tile.type = "button";
            tile.disabled = locked;
            tile.setAttribute("aria-label", "Remove “" + item.tiles[i] + "”");
            tile.addEventListener("click", () => {
                placed.splice(at, 1);
                paint();
            });
            return tile;
        }));
        check.disabled = locked || placed.length !== item.tiles.length;
    };
    check.addEventListener("click", () => send(placed.map((i) => item.tiles[i]), (result) => {
        locked = true;
        tray.classList.add(result.correct ? "is-right" : "is-wrong");
        check.hidden = true;
        paint();
    }));
    paint();
    wrap.append(tray, bank, check);
    return wrap;
}

function showFeedback(box, result, value, item, opts, resolve) {
    box.className = "g-fb " + (result.correct ? "g-fb--ok" : "g-fb--no");
    const parts = [el("div", "g-fb__verdict", result.correct ? "✓ Correct" : "✕ Not quite")];
    if (!result.correct && result.why) {
        const picked = item.type === "choose" ? item.options[value] : value;
        const diag = el("div", "g-fb__why");
        diag.append("Your answer “" + picked + "” — ", rich(result.why));
        parts.push(diag);
    }
    if (!result.correct) {
        const right = el("div", "g-fb__answer", "Correct: ");
        right.append(el("b", null, result.expected));
        parts.push(right);
    }
    parts.push(rich(result.explanation, "g-fb__explain"));
    const extra = opts.footer && opts.footer(result);
    if (extra) parts.push(extra);
    const next = el("button", "btn btn--primary g-next", opts.nextLabel);
    next.type = "button";
    next.addEventListener("click", () => resolve({ correct: result.correct, feedback: result }));
    parts.push(next);
    box.replaceChildren(...parts);
    box.hidden = false;
    next.focus();
}
