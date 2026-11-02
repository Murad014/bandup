package com.bandup.api.grammar.infrastructure.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import com.bandup.api.grammar.application.port.TextSanitizer;

/**
 * {@link TextSanitizer} on jsoup: keeps {@code b}, {@code i} and {@code br}
 * without attributes; other tags are removed (their text stays, except for
 * {@code script} and {@code style}). Emoji, arrows and quotes stay as they are.
 */
@Component
class JsoupTextSanitizer implements TextSanitizer {

    private static final Safelist ALLOWED = new Safelist().addTags("b", "i", "br");
    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings()
            .prettyPrint(false).escapeMode(Entities.EscapeMode.xhtml).charset("UTF-8");

    @Override
    public String clean(String html) {
        return Jsoup.clean(html == null ? "" : html, "", ALLOWED, OUTPUT).strip();
    }
}
