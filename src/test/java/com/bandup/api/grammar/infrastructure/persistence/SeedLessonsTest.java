package com.bandup.api.grammar.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.bandup.TestcontainersConfiguration;
import com.bandup.api.grammar.domain.lesson.aggregate.GrammarLesson;
import com.bandup.api.grammar.domain.lesson.repository.GrammarLessonRepository;
import com.bandup.api.grammar.domain.lesson.service.AnswerNormaliser;
import com.bandup.api.grammar.domain.lesson.valueobject.GivenAnswer;
import com.bandup.api.grammar.domain.lesson.valueobject.GrammarLevel;
import com.bandup.api.grammar.domain.lesson.valueobject.Item;
import com.bandup.api.grammar.domain.lesson.valueobject.LessonSlug;
import com.bandup.api.grammar.domain.lesson.valueobject.Position;
import com.bandup.api.shared.domain.language.LanguageChain;

/**
 * The V10 seed lessons (grammar task 6): one published lesson per level at
 * position 1, each loading through the format rules (a broken seed would fail
 * {@code restore}), and every item accepting its own right answer.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({TestcontainersConfiguration.class, JpaGrammarLessonRepository.class, SeedLessonsTest.Clocks.class})
class SeedLessonsTest {

    private static final Map<GrammarLevel, String> SEEDS = Map.of(
            GrammarLevel.A2, "a2-articles-1",
            GrammarLevel.B1, "b1-present-perfect-past-simple-1",
            GrammarLevel.B2, "b2-second-third-conditional-1",
            GrammarLevel.C1, "c1-inversion-1");

    @TestConfiguration(proxyBeanMethods = false)
    static class Clocks {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired GrammarLessonRepository lessons;

    private final AnswerNormaliser normaliser = new AnswerNormaliser();

    @Test
    void everyLevelStartsWithItsPublishedSeedLesson() {
        assertThat(SEEDS).containsOnlyKeys(Arrays.asList(GrammarLevel.values()));
        SEEDS.forEach((level, slug) -> {
            GrammarLesson lesson = lessons.findBySlug(new LessonSlug(slug)).orElseThrow();
            assertThat(lesson.level()).as(slug).isEqualTo(level);
            assertThat(lesson.isPublished()).as(slug).isTrue();
            assertThat(lesson.position()).as(slug).contains(new Position(1));
            assertThat(lesson.body().learn()).as(slug).hasSizeGreaterThanOrEqualTo(5);
            assertThat(lesson.body().check()).as(slug).hasSizeGreaterThanOrEqualTo(7);
        });
    }

    @Test
    void everySeedItemAcceptsItsOwnRightAnswer() {
        for (String slug : SEEDS.values()) {
            GrammarLesson lesson = lessons.findBySlug(new LessonSlug(slug)).orElseThrow();
            for (Item item : concat(lesson.body().practice(), lesson.body().check())) {
                GivenAnswer right = switch (item) {
                    case Item.Choose c -> new GivenAnswer.Choice(c.answer());
                    case Item.Type t -> new GivenAnswer.Text(t.accept().get(0));
                    case Item.Fix f -> new GivenAnswer.Text(f.accept().get(0));
                    case Item.Build b -> new GivenAnswer.Tiles(Arrays.asList(b.answer().split(" ")));
                };
                assertThat(lesson.grade(item.id(), right, normaliser, LanguageChain.englishOnly()).correct())
                        .as(slug + " " + item.id()).isTrue();
            }
        }
    }

    private static List<Item> concat(List<Item> a, List<Item> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
