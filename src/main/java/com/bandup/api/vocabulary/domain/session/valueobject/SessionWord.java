package com.bandup.api.vocabulary.domain.session.valueobject;

import java.util.List;
import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;
import com.bandup.api.vocabulary.domain.saved.valueobject.SavedWordId;

/**
 * A snapshot of one word's card taken when the session starts, so an admin
 * edit or a regenerated card never changes a session in progress.
 *
 * @param saved    the learner's saved word
 * @param word     the base form shown ("riverbank")
 * @param forms    every accepted spelling (normalised)
 * @param sentence the sentence the learner saved it from; may be null
 * @param from     where it came from (a passage title, or null when typed)
 * @param box      the review box; 0 for a new word
 */
@ValueObject
public record SessionWord(SavedWordId saved, String word, List<String> forms, String pos, String level, String ipa,
        String meaningEn, String meaningAz, List<String> examples, List<String> collocations, List<String> family,
        String sentence, String from, String gapSentence, String gapAnswer, String pairSentence,
        List<String> pairOptions, int pairAnswer, List<String> wrongMeanings, int box) {

    public SessionWord {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(word, "word");
        forms = List.copyOf(forms);
        examples = List.copyOf(examples);
        collocations = List.copyOf(collocations);
        family = List.copyOf(family);
        pairOptions = List.copyOf(pairOptions);
        wrongMeanings = List.copyOf(wrongMeanings);
    }
}
