# Word card format — Vocabulary

The JSON the AI returns for one word (and what an admin edits). The server
parses it, checks every rule below and stores it in `vocab_word.content`.
All values are **plain text** (no HTML, no markdown).

## Request (sent by the app)

```
WORD: riverbanks
CONTEXT (optional): "Their roots slowed the erosion of the riverbanks."
PASSAGE MEANING (optional, dotted words): "the ground at the side of a river" / az: "çay sahili"
```

## Answer

```json
{
  "status": "OK",
  "lemma": "riverbank",
  "forms": ["riverbank", "riverbanks"],
  "pos": "noun",
  "level": "B1",
  "ipa": "/ˈrɪvəbæŋk/",
  "meaning": { "en": "the ground at the side of a river", "az": "çay sahili" },
  "examples": [
    "Willows grew back along the riverbanks.",
    "We walked along the riverbank after lunch."
  ],
  "collocations": ["along the riverbank", "a steep riverbank"],
  "family": ["river (noun)", "bank (noun)"],
  "gap": { "sentence": "The children sat on the ___ and watched the boats.", "answer": "riverbank" },
  "pair": {
    "sentence": "They built a path along the ___.",
    "options": ["riverbank", "erosion", "predator", "decline"],
    "answer": 0
  },
  "wrongMeanings": [
    "a place where people keep their money",
    "the bottom of a lake",
    "a small boat used on rivers"
  ]
}
```

Not a word: `{ "status": "NOT_A_WORD", "reason": "a person's name" }` —
also for rude words, other languages, typos the AI can't place, whole
sentences.

## Rules (checked by `CardPolicy`)

| Field | Rule |
|-------|------|
| `lemma` | 1–3 lower-case English words (letters, `'`, `-`), ≤ 40 chars |
| `forms` | 1–12 distinct values, each a lower-case form of the lemma; includes the lemma and the asked word |
| `pos` | one of `noun`, `verb`, `adjective`, `adverb`, `phrasal verb`, `phrase`, `preposition`, `conjunction`, `other` (or two joined by ` · `) |
| `level` | `A1`, `A2`, `B1`, `B2`, `C1`, `C2` |
| `ipa` | ≤ 60 chars, starts and ends with `/` |
| `meaning.en` | 3–160 chars, simple English (B1 words), does not contain the lemma |
| `meaning.az` | 1–120 chars |
| `examples` | 2 sentences, each 20–160 chars, each contains a form of the word |
| `collocations` | 1–4 values, each ≤ 40 chars, each contains a form of the word |
| `family` | 0–4 values, each ≤ 40 chars |
| `gap.sentence` | 20–160 chars, exactly one `___`; `gap.answer` is one of `forms` |
| `pair.sentence` | 20–160 chars, exactly one `___`; `options` 4 distinct single words or short phrases; `answer` index 0–3; `options[answer]` is one of `forms` |
| `wrongMeanings` | 3 distinct values, 3–160 chars, none equal to `meaning.en` |

Answers are compared normalised: lower case, trimmed, single spaces,
trailing `. ! ? , ;` dropped, `’` → `'`.

## Prompt rules (system prompt of `OpenRouterWordCards`)

- The meaning is the one used in the CONTEXT when given (or the passage
  meaning); otherwise the most common meaning in IELTS / academic English.
- Simple English for the meaning: no harder than B1 words, never the word
  itself.
- Azerbaijani: a natural translation of that meaning (1–5 words, or a short
  phrase).
- Examples: natural, everyday or IELTS-like, no names of real people, brands
  or politics.
- `gap` and `pair`: only one option can be right; the other `pair` options are
  real words of the same part of speech that don't fit.
- `wrongMeanings`: believable but clearly wrong for this word.
- Return ONLY the JSON object.
