package com.bandup.api.vocabulary.application.view;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response records of the learner vocabulary API (design §5.1). No task answers anywhere. */
public final class VocabularyViews {

    private VocabularyViews() {
    }

    /**
     * One saved word.
     *
     * @param forms      every spelling of the word (to find it in a passage)
     * @param cardStatus PREPARING, READY or FAILED
     * @param box        1–4 while LEARNING; null otherwise
     */
    public record SavedWordView(UUID id, String word, List<String> forms, String pos, String level, String ipa, MeaningView meaning,
            SourceView source, LocalDate addedOn, String state, Integer box, LocalDate dueOn, String cardStatus) {
    }

    public record MeaningView(String en, String az) {
    }

    /** @param exercise the passage slug, @param title its title, @param sentence the sentence; all may be null */
    public record SourceView(String exercise, String title, String sentence) {
    }

    /** @param alreadySaved true when the learner had this word already (nothing changed) */
    public record SaveResult(SavedWordView word, boolean alreadySaved) {
    }

    public record Counts(int all, int fresh, int learning, int mastered) {
    }

    /** The My words page. */
    public record WordsPage(Counts counts, int due, int newReady, List<SavedWordView> words, int page, int size,
            long total) {
    }

    /** A card in the bank (lookup and the Meet step). */
    public record CardView(String word, String pos, String level, String ipa, MeaningView meaning,
            List<String> examples, List<String> collocations, List<String> family) {
    }
}
