package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * What the learner reads for one question: a text (statement, question,
 * prompt) or a gap with the text before and after the blank.
 */
@ValueObject
public sealed interface Stem permits Stem.Text, Stem.Gap {

    boolean isBlank();

    record Text(String text) implements Stem {

        public Text {
            text = Objects.requireNonNull(text, "text").strip();
        }

        @Override
        public boolean isBlank() {
            return text.isEmpty();
        }
    }

    /** {@code pre ___ post}; {@code post} may be empty. */
    record Gap(String pre, String post) implements Stem {

        public Gap {
            pre = Objects.requireNonNull(pre, "pre").strip();
            post = post == null ? "" : post.strip();
        }

        @Override
        public boolean isBlank() {
            return pre.isEmpty() && post.isEmpty();
        }
    }
}
