package com.bandup.api.content.infrastructure.html;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.HtmlSanitizer;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;

/**
 * {@link HtmlSanitizer} on jsoup's {@link Cleaner}. Allowed tags: {@code p span
 * mark em strong b i br}; everything else is removed (the text inside
 * unknown tags is kept; {@code script}, {@code style}, {@code iframe} content
 * isn't), with every other attribute, event handler, style and URL. A removed
 * block element leaves a {@code <br>} behind so words don't run together.
 *
 * <p>Passage mode allows on {@code span} only: {@code class} with the values
 * {@code w} / {@code ev}, {@code data-g} on {@code span.w} and {@code id} on
 * {@code span.ev}. Text mode allows no attributes at all, so instructions and
 * explanations can't carry ids that clash with the page's own.
 */
@Component
class JsoupHtmlSanitizer implements HtmlSanitizer {

    private static final String[] TAGS = {"p", "span", "mark", "em", "strong", "b", "i", "br"};
    /** Removed tags whose text stays, and that separate lines: they leave a {@code <br>}. */
    private static final Set<String> TEXT_BLOCKS = Set.of("div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "dt", "dd",
            "blockquote", "pre", "section", "article", "header", "footer", "aside", "main", "address", "figure",
            "figcaption", "tr", "table", "ul", "ol", "dl");
    private static final Set<String> CLASSES = Set.of("w", "ev");

    private static final Cleaner PASSAGE = new Cleaner(new Safelist().addTags(TAGS)
            .addAttributes("span", "class", "id", "data-g"));
    private static final Cleaner TEXT = new Cleaner(new Safelist().addTags(TAGS));

    @Override
    public SanitizedHtml passage(String html) {
        Document clean = PASSAGE.clean(parse(html));
        for (Element span : clean.select("span")) {
            Set<String> classes = new LinkedHashSet<>(span.classNames());
            classes.retainAll(CLASSES);
            if (classes.isEmpty()) {
                span.removeAttr("class");
            } else {
                span.classNames(classes);
            }
            if (!span.hasClass("w")) {
                span.removeAttr("data-g");
            }
            if (!span.hasClass("ev")) {
                span.removeAttr("id");
            }
        }
        return output(clean);
    }

    @Override
    public SanitizedHtml text(String html) {
        return output(TEXT.clean(parse(html)));
    }

    /**
     * Parses the fragment. A block element that will be removed (a heading, a
     * list item, a div...) is followed by a {@code <br>}, so its text doesn't
     * run into the next one ("Wolves" + "return", not "Wolvesreturn").
     */
    private static Document parse(String html) {
        Document document = Jsoup.parseBodyFragment(html == null ? "" : html);
        for (Element element : document.body().select("*")) {
            if (TEXT_BLOCKS.contains(element.normalName()) && element.nextSibling() != null) {
                element.after("<br>");
            }
        }
        return document;
    }

    private static SanitizedHtml output(Document clean) {
        clean.outputSettings().prettyPrint(false).charset(StandardCharsets.UTF_8).escapeMode(Entities.EscapeMode.base);
        return new SanitizedHtml(clean.body().html());
    }
}
