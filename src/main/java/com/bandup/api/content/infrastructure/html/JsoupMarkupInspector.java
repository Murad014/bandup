package com.bandup.api.content.infrastructure.html;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import com.bandup.api.content.application.port.MarkupInspector;
import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;

/**
 * {@link MarkupInspector} on jsoup: every {@code span.w[data-g]} (key and the
 * word's text) and every {@code span.ev[id]}, in document order, as written.
 */
@Component
class JsoupMarkupInspector implements MarkupInspector {

    @Override
    public MarkupKeys inspect(SanitizedHtml passage) {
        Document document = Jsoup.parseBodyFragment(passage.html());
        List<MarkupKeys.GlossMark> glosses = document.select("span.w[data-g]").stream()
                .map(span -> new MarkupKeys.GlossMark(span.attr("data-g"), span.text()))
                .toList();
        List<String> evidence = document.select("span.ev[id]").stream().map(span -> span.attr("id")).toList();
        return new MarkupKeys(glosses, evidence);
    }
}
