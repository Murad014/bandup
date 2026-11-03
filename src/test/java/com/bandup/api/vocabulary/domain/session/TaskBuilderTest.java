package com.bandup.api.vocabulary.domain.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.bandup.api.vocabulary.domain.session.service.AnswerNormaliser;
import com.bandup.api.vocabulary.domain.session.service.TaskBuilder;
import com.bandup.api.vocabulary.domain.session.valueobject.GivenAnswer;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.Task;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskPhase;

class TaskBuilderTest {

    private final TaskBuilder builder = new TaskBuilder();
    private final UnaryOperator<String> normalise = new AnswerNormaliser()::normalise;

    @Test
    void aLearnSessionPractisesThenMastersEachWord() {
        List<Task> tasks = builder.build(SessionKind.LEARN, SessionWords.five(), 42);
        assertThat(tasks).extracting(Task::type).containsExactly("meaning", "match", "gap", "type", "listen", "pair", "translate");
        assertThat(tasks).extracting(Task::phase).containsExactly(TaskPhase.PRACTISE, TaskPhase.PRACTISE, TaskPhase.MASTER,
                TaskPhase.MASTER, TaskPhase.MASTER, TaskPhase.MASTER, TaskPhase.MASTER);
        assertThat(tasks).extracting(t -> t.id().value()).containsExactly("t1", "t2", "t3", "t4", "t5", "t6", "t7");
    }

    @Test
    void twoWordsHaveNoMatch() {
        List<Task> tasks = builder.build(SessionKind.LEARN, SessionWords.five().subList(0, 2), 1);
        assertThat(tasks).extracting(Task::type).containsExactly("meaning", "gap", "type");
    }

    @Test
    void wordsWithTheSameMeaningAreNotMatched() {
        List<SessionWord> synonyms = List.of(SessionWords.word("big", "large in size", 0),
                SessionWords.word("large", "large in size", 0), SessionWords.word("huge", "very big", 0));
        assertThat(builder.build(SessionKind.LEARN, synonyms, 1)).extracting(Task::type).doesNotContain("match");
    }

    @Test
    void aReviewNeverListensAndRotatesByBox() {
        List<SessionWord> words = List.of(SessionWords.word("graze", "eat grass", 1), SessionWords.word("species", "a kind", 2),
                SessionWords.word("cascade", "a series", 3), SessionWords.word("ecosystem", "plants and animals", 1));
        List<Task> tasks = builder.build(SessionKind.REVIEW, words, 7);
        assertThat(tasks).extracting(Task::type).doesNotContain("listen", "meaning", "match");
        assertThat(tasks).extracting(Task::type).containsExactly("gap", "translate", "gap", "pair");
        assertThat(tasks).allMatch(t -> t.phase() == TaskPhase.MASTER);
    }

    @Test
    void noReviewTaskEverListensWhateverTheBoxes() {
        for (int box = 1; box <= 4; box++) {
            List<SessionWord> words = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                words.add(SessionWords.word("word" + (char) ('a' + i), "meaning " + i, box));
            }
            assertThat(builder.build(SessionKind.REVIEW, words, box)).extracting(Task::type)
                    .doesNotContain("listen").contains("pair", "gap", "type", "translate");
        }
    }

    @Test
    void optionsAreShuffledWithTheSeedAndStillGradeRight() {
        List<SessionWord> words = SessionWords.five();
        Task.ChooseMeaning a = (Task.ChooseMeaning) builder.build(SessionKind.LEARN, words, 1).get(0);
        Task.ChooseMeaning b = (Task.ChooseMeaning) builder.build(SessionKind.LEARN, words, 1).get(0);
        assertThat(a.options()).isEqualTo(b.options());
        assertThat(a.options()).containsExactlyInAnyOrder("an animal that hunts others", "wrong predator one",
                "wrong predator two", "wrong predator three");
        assertThat(a.grade(new GivenAnswer.Choice(a.answer()), normalise).correct()).isTrue();
        int wrong = (a.answer() + 1) % 4;
        assertThat(a.grade(new GivenAnswer.Choice(wrong), normalise).why()).contains("predator");
        boolean anyMoved = false;
        for (long seed = 0; seed < 20; seed++) {
            Task.ChooseMeaning t = (Task.ChooseMeaning) builder.build(SessionKind.LEARN, words, seed).get(0);
            anyMoved |= t.answer() != 3;
        }
        assertThat(anyMoved).as("the right meaning isn't always last").isTrue();
    }

    @Test
    void theMatchKnowsEveryWordsMeaning() {
        Task.Match match = (Task.Match) builder.build(SessionKind.LEARN, SessionWords.five(), 3).get(1);
        assertThat(match.terms()).containsExactly("predator", "erosion", "abundant", "decline", "thrive");
        assertThat(match.grade(new GivenAnswer.Pairs(match.answer()), normalise).correct()).isTrue();
        List<Integer> swapped = new java.util.ArrayList<>(match.answer());
        java.util.Collections.swap(swapped, 0, 1);
        assertThat(match.grade(new GivenAnswer.Pairs(swapped), normalise).correct()).isFalse();
    }

    @Test
    void typedAnswersAcceptEveryFormAndShowTheBaseForm() {
        Task.TypeFromMeaning type = (Task.TypeFromMeaning) builder.build(SessionKind.LEARN, SessionWords.five(), 3).get(3);
        assertThat(type.hint()).isEqualTo("e _ _ _ _ _ _");
        assertThat(type.grade(new GivenAnswer.Text(" Erosion. "), normalise).correct()).isTrue();
        assertThat(type.grade(new GivenAnswer.Text("erosions"), normalise).correct()).isTrue();
        assertThat(type.grade(new GivenAnswer.Text("erode"), normalise).expected()).isEqualTo("erosion");
    }

    @Test
    void hintsKeepSpacesAndHyphens() {
        assertThat(TaskBuilder.hint("give up")).isEqualTo("g _ _ _   _ _");
        assertThat(TaskBuilder.hint("well-being")).isEqualTo("w _ _ _ - _ _ _ _ _");
    }
}
