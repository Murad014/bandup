/* =========================================================================
   vocabulary/tasks.js — one vocabulary task on screen and the server's
   feedback (vocabulary requirements 5, 6): meaning (options), match (pairs),
   gap / type / listen / translate (typed), pair (options). The browser never
   knows the answer: opts.answer(value) asks the server, which replies with
   {correct, expected, why, explanation, …}.
   ========================================================================= */

import { el, say } from "./api.js";

const KEYS = ["A", "B", "C", "D"];
const LABEL = { meaning: "choose the meaning", match: "match", gap: "fill the gap", type: "type the word",
    listen: "listen and type", pair: "word pairs", translate: "translate" };

/**
 * Shows `task` in `box` and resolves once the learner has seen the feedback
 * and pressed Continue: {correct, feedback}.
 *
 * @param opts.kick     the label above the task
 * @param opts.header   an optional node above the card (mastery bar)
 * @param opts.answer   value → Promise of the server's feedback
 * @param opts.footer   feedback → an optional node under the explanation
 */
export function showTask(box, task, opts) {
    return new Promise((resolve, reject) => {
        const card = el("div", "v-card v-q");
        card.dataset.task = task.id;
        card.dataset.type = task.type;
        const kick = el("div", "v-q__kick");
        kick.append(el("span", null, opts.kick), el("span", null, LABEL[task.type] || task.type));
        card.append(kick);
        const feedback = el("div", "v-fb");
        feedback.hidden = true;
        feedback.setAttribute("aria-live", "polite");

        let busy = false;
        const send = async (value, onResult) => {
            if (busy) return;
            busy = true;
            try {
                const result = await opts.answer(value);
                onResult(result);
                showFeedback(feedback, result, task, value, opts, resolve);
            } catch (e) {
                busy = false;
                reject(e);
            }
        };

        switch (task.type) {
            case "meaning":
                card.append(el("div", "v-q__prompt", "What does “" + task.term + "” mean?"),
                    options(task.options, send));
                break;
            case "pair":
                card.append(el("div", "v-q__prompt", "Choose the word that fits."), sentence(task.sentence),
                    options(task.options, send, card));
                break;
            case "gap":
                card.append(el("div", "v-q__prompt", "Type the missing word."), sentence(task.sentence),
                    hint(task.hint), typed(send, card));
                break;
            case "type":
                card.append(el("div", "v-q__prompt", task.meaning), hint(task.hint), typed(send, card));
                break;
            case "listen": {
                card.append(el("div", "v-q__prompt", "Type the word you hear."));
                const play = el("div", "v-listen");
                const button = el("button", null, "🔊");
                button.type = "button";
                button.setAttribute("aria-label", "Listen again");
                button.addEventListener("click", () => say(task.speak));
                play.append(button);
                card.append(play, typed(send, card));
                setTimeout(() => say(task.speak), 300);
                break;
            }
            case "translate":
                card.append(el("div", "v-q__prompt", "Type the English word for:"), el("div", "v-az", "🇦🇿 " + task.az),
                    hint(task.hint), typed(send, card));
                break;
            case "match":
                card.append(el("div", "v-q__prompt", "Tap a word, then its meaning."), match(task, send));
                break;
            default:
                card.append(el("p", null, "Unknown task."));
        }
        card.append(feedback);
        box.replaceChildren(...(opts.header ? [opts.header, card] : [card]));
        const first = card.querySelector("input");
        if (first) first.focus();
    });
}

function sentence(text) {
    const line = el("div", "v-q__sentence");
    String(text || "").split("___").forEach((part, i) => {
        if (i > 0) {
            const gap = el("span", "v-gap", "?");
            gap.dataset.gap = "";
            line.append(gap);
        }
        if (part) line.append(document.createTextNode(part));
    });
    return line;
}

function hint(text) {
    const h = el("div", "v-hint", text || "");
    h.setAttribute("aria-label", "Hint: it starts with “" + (text || "").charAt(0) + "”");
    return h;
}

function options(list, send, card) {
    const wrap = el("div", "v-opts");
    const buttons = list.map((option, i) => {
        const b = el("button", "v-opt");
        b.type = "button";
        b.dataset.index = String(i);
        b.append(el("b", "v-opt__key", KEYS[i] || ""), document.createTextNode(option));
        b.addEventListener("click", () => send(i, (result) => {
            buttons.forEach((x) => (x.disabled = true));
            b.classList.add(result.correct ? "is-right" : "is-wrong");
            const right = list.indexOf(result.expected);
            if (!result.correct && right >= 0) buttons[right].classList.add("is-right");
            const gap = card && card.querySelector("[data-gap]");
            if (gap) gap.textContent = result.expected;
        }));
        return b;
    });
    wrap.append(...buttons);
    return wrap;
}

function typed(send, card) {
    const wrap = el("div", "v-typed");
    const input = el("input", "v-answer");
    input.type = "text";
    input.autocomplete = "off";
    input.spellcheck = false;
    input.setAttribute("autocapitalize", "none");
    input.placeholder = "Type the word…";
    input.setAttribute("aria-label", "Your answer");
    const check = el("button", "btn btn--primary", "Check");
    check.type = "button";
    const submit = () => {
        const value = input.value.trim();
        if (!value) return input.focus();
        send(value, (result) => {
            input.disabled = true;
            check.hidden = true;
            input.classList.add(result.correct ? "is-right" : "is-wrong");
            const gap = card.querySelector("[data-gap]");
            if (gap && result.correct) gap.textContent = value;
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

/** Pair every word with a meaning (tap a word, then a meaning; tap again to undo), then Check. */
function match(task, send) {
    const wrap = el("div");
    const pairs = el("div", "v-pairs");
    const left = el("div", "v-pairs__col");
    const right = el("div", "v-pairs__col");
    const picks = task.terms.map(() => null);
    let current = null;
    const check = el("button", "btn btn--primary v-wide v-check", "Check");
    check.type = "button";
    check.disabled = true;
    let locked = false;

    const termButtons = task.terms.map((term, i) => {
        const b = el("button", "v-pair", term);
        b.type = "button";
        b.dataset.term = String(i);
        b.addEventListener("click", () => {
            if (locked) return;
            if (picks[i] !== null) picks[i] = null;
            current = i;
            paint();
        });
        return b;
    });
    const meaningButtons = task.meanings.map((meaning, m) => {
        const b = el("button", "v-pair", meaning);
        b.type = "button";
        b.dataset.meaning = String(m);
        b.addEventListener("click", () => {
            if (locked || current === null) return;
            const owner = picks.indexOf(m);
            if (owner >= 0) picks[owner] = null;
            picks[current] = m;
            current = picks.indexOf(null) >= 0 ? picks.indexOf(null) : null;
            paint();
        });
        return b;
    });

    function paint() {
        termButtons.forEach((b, i) => {
            b.classList.toggle("is-picked", current === i);
            b.classList.toggle("is-matched", picks[i] !== null);
        });
        meaningButtons.forEach((b, m) => {
            const owner = picks.indexOf(m);
            b.classList.toggle("is-matched", owner >= 0);
            b.querySelector("small")?.remove();
            if (owner >= 0) b.prepend(el("small", null, task.terms[owner]));
        });
        check.disabled = locked || picks.some((p) => p === null);
    }

    check.addEventListener("click", () => send(picks.slice(), (result) => {
        locked = true;
        check.hidden = true;
        const expected = new Map(String(result.expected || "").split("; ").map((line) => {
            const at = line.indexOf(" — ");
            return [line.slice(0, at), line.slice(at + 3)];
        }));
        task.terms.forEach((term, i) => {
            const ok = task.meanings[picks[i]] === expected.get(term);
            termButtons[i].classList.add(ok ? "is-right" : "is-wrong");
        });
        termButtons.concat(meaningButtons).forEach((b) => (b.disabled = true));
    }));

    left.append(...termButtons);
    right.append(...meaningButtons);
    pairs.append(left, right);
    current = 0;
    paint();
    wrap.append(pairs, check);
    return wrap;
}

function showFeedback(box, result, task, value, opts, resolve) {
    box.className = "v-fb " + (result.correct ? "v-fb--ok" : "v-fb--no");
    const parts = [el("div", "v-fb__verdict", result.correct ? "✓ Correct" : "✕ Not quite")];
    if (!result.correct) {
        if (result.why) parts.push(el("div", "v-fb__line", result.why));
        else if (typeof value === "string") parts.push(el("div", "v-fb__line", "You typed “" + value + "”."));
        if (task.type === "match") {
            String(result.expected || "").split("; ").forEach((line) => parts.push(el("div", "v-fb__line", line)));
        } else {
            const right = el("div", "v-fb__line", "Correct: ");
            right.append(el("b", null, result.expected));
            parts.push(right);
        }
    }
    if (result.explanation) parts.push(el("div", "v-fb__line", result.explanation));
    const extra = opts.footer && opts.footer(result);
    if (extra) parts.push(extra);
    const next = el("button", "btn btn--primary", "Continue →");
    next.type = "button";
    next.dataset.next = "";
    next.addEventListener("click", () => resolve({ correct: result.correct, feedback: result }));
    parts.push(next);
    box.replaceChildren(...parts);
    box.hidden = false;
    next.focus();
}
