# Lesson format — Grammar

The JSON an AI pipeline (n8n) or an admin sends to
`POST /api/v1/admin/grammar/lessons`. The server validates every rule below on
every save and reports all problems at once (422, `errors[{field, message}]`,
field = JSON path such as `check[3].answer`).

## Text values

Every learner-facing text (`Text` below) is either a string (English) or an
object of languages:

```json
"goal": { "en": "By the end, you'll know …", "az": "Sonda … biləcəksiniz." }
```

`en` is required. HTML allowed: `<b>`, `<i>`, `<br>` only (the rest is
removed). Plain values (`answer`, `accept`, `tiles`, `options`) are plain text,
no HTML.

## Example (shortened)

```json
{
  "id": "b1-present-perfect-vs-past-simple-1",
  "level": "B1",
  "topic": { "key": "present-perfect", "label": "Present perfect vs past simple" },
  "title": "Present Perfect vs Past Simple",
  "goal": "By the end, you'll know how to decide present perfect vs past simple — and produce both.",
  "minutes": 20,
  "position": null,
  "learn": [
    { "icon": "🌉", "label": "The idea", "heading": "Present perfect links past to NOW",
      "body": "<b>have/has + past participle</b>. The connection to now matters.",
      "examples": [ { "ok": true, "text": "I <b>have lost</b> my keys." } ] },
    { "icon": "🧭", "label": "How to decide", "heading": "Ask 2 questions, in order",
      "steps": [ "1️⃣ Is there a <b>finished-time word</b>? → <b>Past simple</b>",
                 "2️⃣ <b>since / for / ever / just</b>? → <b>Present perfect</b>" ],
      "tip": "Check the time words first — they decide it." }
  ],
  "worked": {
    "question": "Choose the verb: “I ___ (visit) London in 2019.”",
    "steps": [ "🔍 Find the time clue: <b>in 2019</b>.", "✅ Finished time → <b>visited</b>." ],
    "answer": "visited"
  },
  "practice": [
    { "id": "p1", "type": "choose", "prompt": "I ___ my wallet — I can't find it!",
      "options": [ "lost", "have lost" ], "answer": 1,
      "whys": [ "the result matters now", "✓ result affects now → present perfect" ],
      "explanation": "Result matters now → present perfect." }
  ],
  "check": [
    { "id": "c1", "type": "type", "prompt": "Type the correct form of “win”:",
      "sentence": "They ___ the title in 2021.", "accept": [ "won" ], "show": "won",
      "explanation": "“in 2021” = finished time → <b>won</b>." },
    { "id": "c2", "type": "fix", "prompt": "This sentence is wrong. Type the correct verb:",
      "wrong": "I have seen him yesterday.", "accept": [ "saw" ], "show": "saw",
      "explanation": "“yesterday” blocks present perfect → <b>saw</b>." },
    { "id": "c3", "type": "build", "prompt": "Build the sentence:",
      "tiles": [ "since", "We", "him", "have", "2019", "known" ],
      "answer": "We have known him since 2019",
      "explanation": "“since” → present perfect." }
  ]
}
```

## Fields

| Field | Rule |
|-------|------|
| `id` | the slug: lowercase letters, digits, `-`; 3–80 chars; unique (409 if taken on create) |
| `level` | `A2`, `B1`, `B2` or `C1` |
| `topic.key` | lowercase slug, 2–60 chars (lessons with the same key form one topic) |
| `topic.label` | Text, ≤ 80 chars |
| `title` | Text, ≤ 120 chars |
| `goal` | Text, ≤ 300 chars |
| `minutes` | 5–40, default 20 |
| `position` | optional, ≥ 1; empty → set to the end of the course on publish |
| `learn` | 3–8 cards |
| `learn[].icon` | one emoji (≤ 8 chars) |
| `learn[].label`, `heading` | Text, required |
| `learn[].body` / `steps` / `examples` | at least one of them; `steps` 1–5 Texts; `examples` 1–4 of `{ok: bool, text: Text}` |
| `learn[].tip` | optional Text |
| `worked` | required: `question` Text, `steps` 2–5 Texts, `answer` plain text |
| `practice` | 2–6 items, ids `p1`, `p2`, … |
| `check` | 5–15 items, ids `c1`, `c2`, …; at least 2 different types |
| `explanation` | Text, required on every item |

Text lengths (each language): `topic.label` 80, `title` 120, `goal` 300,
`learn[].label` 80, `learn[].heading` 160, `learn[].body` 1000, `tip`, steps,
examples, `worked.question`, `prompt`, `whys[]` 300, `explanation` 600.

Item ids are unique in the lesson and are never reused for a different
question after publishing (review cards point at them).

## Item types

| Type | Fields | Rules |
|------|--------|-------|
| `choose` | `prompt`, `options` (2–4 plain), `answer` (0-based index), `whys` (optional, one Text per option) | options unique (ignoring case); `answer` in range; `whys` length = options length |
| `type` | `prompt`, `sentence` (exactly one `___`), `accept` (1–5 plain), `show` (optional, default `accept[0]`) | accepted values unique after normalising; ≤ 4 words each |
| `fix` | `prompt`, `wrong` (plain sentence), `accept` (1–5 plain), `show` (optional) | `wrong` must not itself be an accepted answer |
| `build` | `prompt`, `tiles` (2–12 plain), `answer` (plain sentence) | the answer's words are exactly the tiles (same multiset); tiles must not already be in answer order |

`prompt` (and `sentence`) are Text. `___` marks the gap in `prompt` or
`sentence` for `choose` and `type`.

## Grading (server)

- Normalising: lowercase, trim, collapse spaces, drop trailing `. ! ? , ;`,
  `’` → `'`.
- `choose`: the picked index equals `answer`.
- `type` / `fix`: the normalised answer equals one normalised `accept` value.
- `build`: the normalised joined tiles equal the normalised `answer`.

## The AI generator (n8n)

`ai-docs/n8n/Orbiko - Grammar Generator.json` (requirement 13; import it into
n8n, set *Orbiko settings* and the OpenRouter credential):

```
Form → Build brief → Claude - Write lesson → Code validators ─ok→ Prepare solve
                          ↑ Prepare retry ←─problems─┘            → GPT - Blind solve → Compare
Code validators ← Claude - Fix flagged items ← Prepare fix ←─flagged─┘    │ all correct
                                                    Build Orbiko JSON → Login → Create draft → Done
```

- **Build brief**: id `<level>-<topic key>-<no>`, level language (A2 ≤ 12
  words a sentence … C1 academic register), 3 practice and 7 (A2, B1) or 8
  (B2, C1) check items, Azerbaijani on request.
- **Writer prompt** (Claude): one rule; "How to decide" steps that the worked
  example applies; a why per wrong option naming the likely mistake; check
  items test production (≥ 2 choose, ≥ 1 type, fix and build); every correct
  variant in `accept`; plain values without HTML, texts with `<b>/<i>/<br>`
  only; with Azerbaijani: `{en, az}` for goal, learn texts and explanations.
  For lesson 2+ of a topic: go deeper, don't repeat lesson 1.
- **Code validators**: every rule of this file (same messages and paths as
  the server, plus the brief's id/level/topic, no markdown, no `position`,
  the item mix). Keep it in step with `LessonPolicy` when a rule changes.
- **Blind solve** (GPT) gets the items without `answer`, `accept`, `whys`,
  `explanation`; **Compare** flags a different answer, a second correct
  answer not accepted, or a note.
