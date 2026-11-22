/* =========================================================================
   vocabulary/sets.js — /vocabulary/sets (vocabulary requirement 6.3): the
   learner's learned words in sets of 25 — a dot per word, mastered / open /
   locked, and "Start round N →" on the open set.
   ========================================================================= */

import { currentUser } from "../auth.js";
import * as api from "./api.js";
import { SET_SIZE, dots, el } from "./api.js";

const $ = (id) => document.getElementById(id);

function card(set, all) {
    const box = el("section", "v-card v-set v-set--" + set.status.toLowerCase());
    box.dataset.set = String(set.number);
    const head = el("div", "v-set__head");
    head.append(el("h2", null, "Set " + set.number),
        el("span", "v-chip", set.size < SET_SIZE ? set.size + " / " + SET_SIZE + " words" : set.size + " words"),
        el("span", "v-grow"),
        el("span", "v-set__status", { MASTERED: "✓ Mastered", OPEN: set.mastered + " / " + set.size + " mastered",
            LOCKED: "🔒 Locked" }[set.status]));
    box.append(head, dots(set.streaks, set.size < SET_SIZE));
    let text;
    if (set.status === "MASTERED") {
        text = (set.rounds ? "Mastered in " + set.rounds + " round" + (set.rounds === 1 ? "" : "s") + ". " : "")
            + "These words aren't asked again.";
    } else if (set.status === "OPEN") {
        const left = set.size - set.mastered;
        text = left + " word" + (left === 1 ? "" : "s") + " left · round " + (set.rounds + 1) + " asks only these.";
    } else {
        text = "Opens when Set " + (set.number - 1) + " is mastered."
            + (set.size < SET_SIZE ? " Fills up as you learn words (" + set.size + " / " + SET_SIZE + ")." : "");
    }
    box.append(el("p", null, text));
    if (set.status === "OPEN") {
        const go = el("a", "btn btn--primary", "Start round " + (set.rounds + 1) + " →");
        go.href = "/vocabulary/round";
        go.id = "vsStart";
        box.append(go);
    }
    return box;
}

async function start() {
    const user = await currentUser();
    $("vsStatus").hidden = true;
    if (!user) {
        $("vsGuest").hidden = false;
        return;
    }
    try {
        const sets = await api.sets();
        $("vsList").replaceChildren(...sets.sets.map((s) => card(s, sets.sets)));
        $("vsEmpty").hidden = sets.sets.length > 0;
        $("vsPage").hidden = false;
    } catch (e) {
        $("vsStatus").hidden = false;
        $("vsStatus").textContent = "We couldn't load your sets. Check your connection and reload the page.";
    }
}

start();
