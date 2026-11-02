package com.bandup.api.grammar.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.bandup.api.grammar.application.view.LearnerViews;

/**
 * Answer keys never reach a learner before they answer (grammar requirement
 * 7): no learner response record has a key field, except the feedback that
 * answers one item and the worked example (a teaching step); and the course
 * read model never reads the lesson's items.
 */
class GrammarKeysTest {

    private static final Set<String> KEY_FIELDS = Set.of("answer", "accept", "show", "whys", "why", "expected",
            "explanation", "check", "practice");
    /** Records allowed to carry some of them: feedback after an answer, and the worked example's answer. */
    private static final Set<Class<?>> FEEDBACK = Set.of(LearnerViews.AnswerView.class,
            LearnerViews.ReviewAnswerView.class);

    @Test
    void noLearnerViewBeforeAnAnswerHasAKeyField() {
        List<Class<?>> views = Arrays.stream(LearnerViews.class.getDeclaredClasses()).filter(Class::isRecord).toList();
        assertThat(views).hasSizeGreaterThan(10);
        for (Class<?> view : views) {
            if (FEEDBACK.contains(view)) {
                continue;
            }
            List<String> leaking = Arrays.stream(view.getRecordComponents()).map(RecordComponent::getName)
                    .filter(KEY_FIELDS::contains)
                    .filter(name -> !(view == LearnerViews.WorkedView.class && name.equals("answer")))
                    .filter(name -> !(view == LearnerViews.RunView.class && (name.equals("practice") || name.equals("check"))))
                    .toList();
            assertThat(leaking).as(view.getSimpleName() + " must not carry answer keys").isEmpty();
        }
    }

    @Test
    void theRunViewListsItemViewsOnly() {
        assertThat(Arrays.stream(LearnerViews.RunView.class.getRecordComponents())
                .filter(c -> c.getName().equals("practice") || c.getName().equals("check"))
                .map(c -> c.getGenericType().getTypeName()))
                .allMatch(type -> type.endsWith("LearnerViews$ItemView>"));
    }

    @Test
    void theCourseReadModelNeverReadsTheItems() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/java/com/bandup/api/grammar/infrastructure/readmodel/JdbcLearnerCourses.java"));
        assertThat(sql).doesNotContain("'check'", "'practice'", "'worked'", "'learn'", "content ->> 'check'",
                "content::text", "select content", "l.content,", "l.content as");
    }
}
