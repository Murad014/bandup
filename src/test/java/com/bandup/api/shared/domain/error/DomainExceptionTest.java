package com.bandup.api.shared.domain.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void carriesEveryViolationAndAReadableMessage() {
        DomainException e = new DomainException(List.of(
                new Violation("title", "Title is required."),
                new Violation("", "Add at least one question before publishing.")));

        assertThat(e.violations()).extracting(Violation::field).containsExactly("title", "");
        assertThat(e.getMessage()).isEqualTo("title: Title is required.; Add at least one question before publishing.");
    }

    @Test
    void needsAtLeastOneViolation() {
        assertThatThrownBy(() -> new DomainException(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void violationNeedsAFieldAndAMessage() {
        assertThatThrownBy(() -> new Violation(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Violation("title", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DomainException.of("title", "Title is required.").violations())
                .containsExactly(new Violation("title", "Title is required."));
    }
}
