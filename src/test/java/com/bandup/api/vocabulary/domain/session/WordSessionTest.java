package com.bandup.api.vocabulary.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.bandup.api.shared.domain.error.DomainException;
import com.bandup.api.vocabulary.domain.saved.valueobject.LearnerId;
import com.bandup.api.vocabulary.domain.session.aggregate.WordSession;
import com.bandup.api.vocabulary.domain.session.service.AnswerNormaliser;
import com.bandup.api.vocabulary.domain.session.service.TaskBuilder;
import com.bandup.api.vocabulary.domain.session.valueobject.GivenAnswer;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionId;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionKind;
import com.bandup.api.vocabulary.domain.session.valueobject.SessionWord;
import com.bandup.api.vocabulary.domain.session.valueobject.Task;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskFeedback;
import com.bandup.api.vocabulary.domain.session.valueobject.TaskId;

class WordSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private final UnaryOperator<String> normalise = new AnswerNormaliser()::normalise;
    private final LearnerId leyla = new LearnerId(UUID.randomUUID());
    private final List<SessionWord> three = SessionWords.five().subList(0, 3);
    private final WordSession session = WordSession.start(SessionId.random(), leyla, SessionKind.LEARN, three,
            new TaskBuilder().build(SessionKind.LEARN, three, 5), NOW);

    @Test
    void practiseIsAnsweredOnceAndAWrongMasterTaskComesBack() {
        assertThat(session.queue()).extracting(TaskId::value).containsExactly("t1", "t2", "t3", "t4", "t5");
        TaskFeedback f = session.answer(TaskId.of(1), wrongChoice(task("t1")), normalise, NOW);
        assertThat(f.correct()).isFalse();
        assertThat(session.queue()).as("practise isn't repeated").extracting(TaskId::value).containsExactly("t2", "t3", "t4", "t5");
        Task.Match match = (Task.Match) task("t2");
        session.answer(TaskId.of(2), new GivenAnswer.Pairs(match.answer()), normalise, NOW);

        f = session.answer(TaskId.of(3), new GivenAnswer.Text("nope"), normalise, NOW);
        assertThat(f.correct()).isFalse();
        assertThat(f.expected()).isEqualTo("predator");
        assertThat(f.explanation()).isEqualTo("predator — an animal that hunts others");
        assertThat(session.queue()).extracting(TaskId::value).containsExactly("t4", "t5", "t3");
        assertThat(session.retries()).isEqualTo(1);

        session.answer(TaskId.of(4), new GivenAnswer.Text("erosion"), normalise, NOW);
        f = session.answer(TaskId.of(5), new GivenAnswer.Text("abundant"), normalise, NOW);
        assertThat(f.mastered()).isEqualTo(2);
        assertThat(f.total()).isEqualTo(3);
        assertThat(f.finished()).isFalse();
        f = session.answer(TaskId.of(3), new GivenAnswer.Text("Predator"), normalise, NOW);
        assertThat(f.finished()).isTrue();
        assertThat(session.isFinished()).isTrue();
        assertThat(session.finishedAt()).contains(NOW);
    }

    @Test
    void onlyWaitingTasksTakeAnswers() {
        assertThatThrownBy(() -> session.answer(TaskId.of(9), new GivenAnswer.Text("x"), normalise, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("taskId");
        session.answer(TaskId.of(1), new GivenAnswer.Choice(0), normalise, NOW);
        assertThatThrownBy(() -> session.answer(TaskId.of(1), new GivenAnswer.Choice(0), normalise, NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void anAnswerOfTheWrongKindIsRefused() {
        assertThatThrownBy(() -> session.answer(TaskId.of(1), new GivenAnswer.Text("x"), normalise, NOW))
                .isInstanceOf(DomainException.class).hasMessageContaining("answer");
        assertThatThrownBy(() -> session.answer(TaskId.of(1), new GivenAnswer.Choice(7), normalise, NOW))
                .isInstanceOf(DomainException.class);
        assertThat(session.queue()).hasSize(5);
    }

    @Test
    void aFinishedSessionTakesNoMoreAnswers() {
        List<SessionWord> one = SessionWords.five().subList(0, 1);
        WordSession review = WordSession.start(SessionId.random(), leyla, SessionKind.REVIEW, one,
                new TaskBuilder().build(SessionKind.REVIEW, one, 1), NOW);
        Task only = review.tasks().get(0);
        review.answer(only.id(), right(only), normalise, NOW);
        assertThat(review.isFinished()).isTrue();
        assertThatThrownBy(() -> review.answer(only.id(), right(only), normalise, NOW)).isInstanceOf(IllegalStateException.class);
    }

    private Task task(String id) {
        return session.task(new TaskId(id)).orElseThrow();
    }

    private static GivenAnswer wrongChoice(Task task) {
        return new GivenAnswer.Choice((((Task.ChooseMeaning) task).answer() + 1) % 4);
    }

    private static GivenAnswer right(Task task) {
        return switch (task) {
            case Task.Pair p -> new GivenAnswer.Choice(p.answer());
            case Task.Gap g -> new GivenAnswer.Text(g.answer());
            case Task.TypeFromMeaning t -> new GivenAnswer.Text(t.accept().get(0));
            case Task.Translate t -> new GivenAnswer.Text(t.accept().get(0));
            default -> throw new IllegalArgumentException(task.type());
        };
    }
}
