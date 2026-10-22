package com.bandup.api.content.domain.exercise.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.shared.domain.error.DomainException;

class ContentValueObjectsTest {

    @Test
    void slugsAreLowerCaseWordsJoinedByHyphens() {
        assertThat(new Slug("yellowstone-wolves").value()).isEqualTo("yellowstone-wolves");
        assertThat(new Slug("ielts-2026")).isNotNull();
        for (String bad : new String[] {"", "Wolves", "two--hyphens", "-start", "end-", "with space", "x".repeat(121)}) {
            assertThatThrownBy(() -> new Slug(bad)).as(bad).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.violations().getFirst().field()).isEqualTo("slug"));
        }
        assertThatThrownBy(() -> new Slug(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void timerNeedsAPositiveDurationAndAWarningWithinIt() {
        assertThat(new Timer(300, 60).lowTimeThreshold()).contains(60);
        assertThat(new Timer(300, null).lowTimeThreshold()).isEmpty();
        assertThat(new Timer(300, 300)).isNotNull();
        assertThatThrownBy(() -> new Timer(0, null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.violations().getFirst().field()).isEqualTo("durationSeconds"));
        assertThatThrownBy(() -> new Timer(300, 301)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.violations().getFirst().field()).isEqualTo("lowTimeThresholdSeconds"));
        assertThatThrownBy(() -> new Timer(300, 0)).isInstanceOf(DomainException.class);
    }

    @Test
    void keysHaveTheirFormats() {
        assertThat(GlossKey.isValid("g12")).isTrue();
        assertThat(GlossKey.isValid("G12")).isFalse();
        assertThat(GlossKey.isValid("g")).isFalse();
        assertThat(EvidenceKey.isValid("e4")).isTrue();
        assertThat(EvidenceKey.isValid("ev4")).isFalse();
        assertThatThrownBy(() -> new GlossKey("x1")).isInstanceOf(DomainException.class);
        assertThat(new OptionKey("iv").value()).isEqualTo("iv");
        assertThatThrownBy(() -> new OptionKey("A B")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new OptionKey("")).isInstanceOf(DomainException.class);
    }

    @Test
    void answerKeysDropBlanksAndRejectRepeats() {
        assertThat(new AnswerKey(List.of(" riverbanks ", "", "river banks")).values()).containsExactly("riverbanks", "river banks");
        assertThat(AnswerKey.none().isEmpty()).isTrue();
        assertThatThrownBy(() -> AnswerKey.of("A", "A")).isInstanceOf(DomainException.class);
    }

    @Test
    void wordLimitAndWordCount() {
        assertThat(new WordLimit(2, true).allows("17 grey wolves")).isTrue();
        assertThat(new WordLimit(2, false).allows("17 grey wolves")).isFalse();
        assertThat(new SanitizedHtml("<p>One <b>two</b>&nbsp;three</p>").wordCount()).isEqualTo(3);
        assertThat(new SanitizedHtml("<p> </p>").isBlank()).isTrue();
        assertThat(new SanitizedHtml("").wordCount()).isZero();
    }

    @Test
    void questionTypesKnowTheirFixedChoicesAndPictures() {
        assertThat(new QuestionType("TFNG", QuestionFamily.CHOICE_FIXED).fixedChoices()).containsExactly("TRUE", "FALSE", "NOT GIVEN");
        assertThat(new QuestionType("MCQ", QuestionFamily.MC_SINGLE).fixedChoices()).isEmpty();
        assertThat(new QuestionType("DIAGRAM_LABEL", QuestionFamily.GAP).needsImage()).isTrue();
        assertThat(QuestionFamily.ofCode("mcMulti")).isEqualTo(QuestionFamily.MC_MULTI);
        assertThatThrownBy(() -> new QuestionType("tfng", QuestionFamily.CHOICE_FIXED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stemsAndRevisions() {
        assertThat(new Stem.Gap(" Elk ate ", null).post()).isEmpty();
        assertThat(new Stem.Text("  ").isBlank()).isTrue();
        assertThat(Revision.first().next()).isEqualTo(new Revision(2));
        assertThatThrownBy(() -> new Placement(new StageId(1), 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new GlossMeaning(" ", TextSource.HUMAN, false)).isInstanceOf(DomainException.class);
    }
}
