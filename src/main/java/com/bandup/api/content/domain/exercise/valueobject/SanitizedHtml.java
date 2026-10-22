package com.bandup.api.content.domain.exercise.valueobject;

import java.util.Objects;

import com.bandup.api.shared.domain.ddd.ValueObject;

/**
 * Author HTML that has been through the allow-list sanitizer (backend
 * requirement 5.1). The application creates it only from the
 * {@code HtmlSanitizer} port's output; the domain never sees raw author HTML.
 */
@ValueObject
public record SanitizedHtml(String html) {

    public SanitizedHtml {
        Objects.requireNonNull(html, "html");
    }

    public boolean isBlank() {
        return html.replaceAll("<[^>]*>", "").isBlank();
    }

    /** Words of the visible text (tags removed), for {@code reading_passage.word_count}. */
    public int wordCount() {
        String text = html.replaceAll("<[^>]*>", " ").replaceAll("&[a-zA-Z#0-9]+;", " ").strip();
        return text.isEmpty() ? 0 : text.split("\\s+").length;
    }
}
