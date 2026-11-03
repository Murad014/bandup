package com.bandup.api.vocabulary.infrastructure.ai;

import lombok.experimental.UtilityClass;

import com.bandup.api.vocabulary.application.port.WordCardAi.CardRequest;

/** The prompt for one word card (card-format.md, "Prompt rules"). */
@UtilityClass
final class CardPrompt {

    static final String SYSTEM = """
            You write vocabulary cards for IELTS learners whose first language is Azerbaijani.
            You get one English WORD (maybe in a CONTEXT sentence). Return ONLY one JSON object, no other text.

            If the WORD is not an English word or short phrase we should teach (a person's or brand name, a rude word,
            another language, a typo you can't place, a whole sentence), return:
            {"status": "NOT_A_WORD", "reason": "<a few words>"}

            Otherwise return:
            {
              "status": "OK",
              "lemma": "<base form, lower case>",
              "forms": ["<the lemma>", "<the asked word>", "<other common forms>"],
              "pos": "noun | verb | adjective | adverb | phrasal verb | phrase | preposition | conjunction | other (two may be joined with ' · ')",
              "level": "A1 | A2 | B1 | B2 | C1 | C2",
              "ipa": "/…/ (British)",
              "meaning": {"en": "<simple English, B1 words, never the word itself, at most 160 chars>", "az": "<natural Azerbaijani, 1-5 words>"},
              "examples": ["<sentence using the word, 20-160 chars>", "<another one>"],
              "collocations": ["<1-4 common word pairs that contain the word, each at most 40 chars>"],
              "family": ["<0-4 related words, e.g. 'erode (verb)'>"],
              "gap": {"sentence": "<a 20-160 char sentence with ___ where one form of the word goes>", "answer": "<that form>"},
              "pair": {"sentence": "<a natural phrase with one ___>", "options": ["<4 single words or short phrases>"], "answer": <index of the right one>},
              "wrongMeanings": ["<3 believable but clearly wrong meanings, at most 160 chars each>"]
            }

            Rules:
            - The meaning is the one used in the CONTEXT (or the PASSAGE MEANING) when given; otherwise the most common
              meaning in IELTS / academic English.
            - Examples are natural, everyday or IELTS-like; no real people, brands or politics.
            - In "gap" and "pair" only one answer can be right; the other "pair" options are real words of the same
              part of speech that don't fit. The right option is a form of the word.
            - Plain text only: no HTML, no markdown.
            """;

    static String user(CardRequest r) {
        StringBuilder text = new StringBuilder("WORD: ").append(r.word());
        if (r.sentence() != null) {
            text.append("\nCONTEXT: \"").append(r.sentence().replace('"', '\'')).append('"');
        }
        if (r.passageEn() != null) {
            text.append("\nPASSAGE MEANING: \"").append(r.passageEn()).append('"');
            if (r.passageAz() != null) {
                text.append(" / az: \"").append(r.passageAz()).append('"');
            }
        }
        if (!r.problems().isEmpty()) {
            text.append("\n\nYour previous answer had these problems; fix all of them:\n- ")
                    .append(String.join("\n- ", r.problems()));
        }
        return text.toString();
    }
}
