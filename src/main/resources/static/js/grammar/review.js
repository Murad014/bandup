/* =========================================================================
   grammar/review.js — /grammar/review (grammar requirement 6).
   Up to 20 due cards from finished lessons, each labelled with its topic.
   The server grades every answer and moves the card ("⏱ Next review: …");
   a wrong card comes back at the end of the session until it is right
   (only the first answer of the day changes its schedule — server rule).
   Answers are saved one by one, so leaving early loses nothing.
   ========================================================================= */

import { ApiError } from "../auth.js";
import * as api from "./api.js";
import { el, showItem } from "./items.js";

const $ = (id) => document.getElementById(id);
const body = $("rbody");

function link(label, href, className) {
    const a = el("a", "btn " + (className || ""), label);
    a.href = href;
    return a;
}

function message(title, text, actions) {
    const card = el("div", "g-card g-msg");
    card.append(el("h1", null, title), el("p", null, text));
    const row = el("div", "g-msg__actions");
    row.append(...actions);
    card.append(row);
    body.replaceChildren(card);
}

function failed(error) {
    if (error instanceof ApiError && error.status === 401) {
        message("Sign in to review", "Finished lessons come back here as a daily review once you sign in.",
            [link("Sign in", "/login?next=" + encodeURIComponent("/grammar/review"), "btn--primary"), link("Back to course", "/grammar")]);
    } else {
        const again = el("button", "btn btn--primary", "Try again");
        again.type = "button";
        again.addEventListener("click", () => location.reload());
        message("Something went wrong", "We couldn't reach Orbiko. Check your connection and try again.",
            [again, link("Back to course", "/grammar")]);
    }
}

/**
 * "tomorrow", "in 3 days", "in 1 week", "in 2 weeks". {@code days} is counted by
 * the server from its today: the browser's clock may be on another day.
 */
export function nextReviewText(days) {
    if (days <= 0) return "today";
    if (days === 1) return "tomorrow";
    if (days % 7 === 0) return "in " + days / 7 + (days === 7 ? " week" : " weeks");
    return "in " + days + " days";
}

function bar(reviewed, total) {
    const box = el("div", "g-mastery");
    const track = el("div", "g-mastery__track");
    const fill = el("i");
    fill.style.width = Math.round((reviewed / total) * 100) + "%";
    track.append(fill);
    const count = el("b", null, reviewed + " / " + total);
    count.id = "reviewCount";
    box.append(el("span", null, "Reviewed"), track, count);
    return box;
}

async function session(data) {
    const total = data.cards.length;
    const queue = [...data.cards];
    let reviewed = 0;
    let retries = 0;
    while (queue.length) {
        const card = queue.shift();
        let result;
        try {
            result = await showItem(body, card.item, {
                kick: "🏷 " + card.topic.label + " · " + card.lessonTitle,
                header: bar(reviewed, total),
                nextLabel: "Continue →",
                answer: (value) => api.answerCard(card.cardId, value),
                footer: (feedback) => {
                    const box = el("div");
                    if (!feedback.correct) box.append(el("div", "g-fb__retry", "↻ This comes back until you get it right."));
                    box.append(el("div", "g-fb__next", "⏱ Next review: " + nextReviewText(feedback.nextInDays)));
                    return box;
                },
            });
        } catch (e) {
            failed(e);
            return;
        }
        if (result.correct) reviewed += 1;
        else {
            retries += 1;
            queue.push(card);
        }
    }
    done(total, retries, data.dueTotal - total);
}

function done(total, retries, more) {
    const card = el("div", "g-card g-done");
    card.id = "reviewDone";
    card.append(el("div", "g-done__icon", "✅"), el("h1", null, "Review done"),
        el("p", null, more > 0 ? more + " more " + (more === 1 ? "card is" : "cards are") + " due — keep going or come back later."
            : "Every due card is reviewed. See you at the next review!"));
    const recap = el("div", "g-recap");
    for (const [label, value] of [["Reviewed", total + (total === 1 ? " card" : " cards")], ["Retries", String(retries)]]) {
        const row = el("div");
        row.append(el("b", null, label + ": "), document.createTextNode(value));
        recap.append(row);
    }
    const actions = el("div", "g-msg__actions");
    if (more > 0) {
        const again = el("button", "btn btn--primary", "Review more");
        again.type = "button";
        again.id = "reviewMore";
        again.addEventListener("click", start);
        actions.append(again);
    }
    actions.append(link("Back to course", "/grammar", more > 0 ? "" : "btn--primary"));
    card.append(recap, actions);
    body.replaceChildren(card);
}

async function start() {
    $("rstatus").hidden = false;
    body.replaceChildren();
    let data;
    try {
        data = await api.review();
    } catch (e) {
        $("rstatus").hidden = true;
        failed(e);
        return;
    }
    $("rstatus").hidden = true;
    if (!data.cards.length) {
        message("Nothing to review", "Nothing is due right now. Rules from finished lessons come back here when they are due.",
            [link("Back to course", "/grammar", "btn--primary")]);
        body.firstChild.id = "reviewEmpty";
        return;
    }
    session(data);
}

start();
