package com.bandup.api.content.infrastructure.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bandup.api.content.domain.exercise.valueobject.MarkupKeys;
import com.bandup.api.content.domain.exercise.valueobject.SanitizedHtml;

/** The allow-list (backend requirement 5.1) and key extraction (5.2), without Spring. */
class JsoupHtmlAdaptersTest {

    private final JsoupHtmlSanitizer sanitizer = new JsoupHtmlSanitizer();
    private final JsoupMarkupInspector inspector = new JsoupMarkupInspector();

    // ------------------------------------------------------------ removed

    @Test
    void scriptsStylesAndFramesAreRemovedWithTheirContent() {
        assertThat(passage("<p>Hi<script>alert(1)</script><style>p{}</style><iframe src=\"x\">frame</iframe></p>"))
                .isEqualTo("<p>Hi</p>");
    }

    @Test
    void eventHandlersStylesAndLinksAreRemoved() {
        assertThat(passage("<p onclick=\"alert(1)\" style=\"color:red\">a</p><span onmouseover=\"x()\" class=\"w\" data-g=\"g1\">b</span>"))
                .isEqualTo("<p>a</p><span class=\"w\" data-g=\"g1\">b</span>");
        assertThat(passage("<a href=\"javascript:alert(1)\">click</a> <img src=x onerror=alert(1)>"))
                .isEqualTo("click ");
        assertThat(passage("<svg><script>alert(1)</script></svg><math><mi>x</mi></math>")).doesNotContain("<", "alert");
    }

    @Test
    void unknownTagsAndAttributesGoButTheirTextStays() {
        assertThat(passage("<div title=\"t\"><h1>Wolves</h1><u>return</u></div>")).isEqualTo("Wolves<br>return");
        assertThat(passage("<ul><li>one</li><li>two</li></ul><p>three</p>")).isEqualTo("one<br>two<br><p>three</p>");
        assertThat(passage("<p data-x=\"1\" lang=\"en\">text</p>")).isEqualTo("<p>text</p>");
        assertThat(passage("<!-- secret --><p>shown</p>")).isEqualTo("<p>shown</p>");
    }

    @Test
    void theOldDataAzAttributeIsGone() {
        assertThat(passage("<span class=\"w\" data-az=\"məhv edilmək\">eliminated</span>"))
                .isEqualTo("<span class=\"w\">eliminated</span>");
    }

    // ------------------------------------------------------------ kept

    @Test
    void readingAidsAndEmphasisAreKept() {
        String html = "<p><span class=\"ev\" id=\"e1\">Wolves were <span class=\"w\" data-g=\"g1\">eliminated</span></span> "
                + "<strong>TRUE</strong>, <b>b</b>, <i>i</i>, <em>em</em>, <mark>m</mark><br>next</p>";
        assertThat(passage(html)).isEqualTo(html);
    }

    @Test
    void classesAreLimitedToTheReadingAidsAndKeysToTheirSpans() {
        assertThat(passage("<span class=\"w highlight\" data-g=\"g2\" id=\"e9\">a</span>"))
                .isEqualTo("<span class=\"w\" data-g=\"g2\">a</span>");
        assertThat(passage("<span class=\"ev other\" id=\"e3\" data-g=\"g3\">a</span>"))
                .isEqualTo("<span class=\"ev\" id=\"e3\">a</span>");
        assertThat(passage("<span id=\"stageLabel\" data-g=\"g4\" class=\"x\">a</span><p id=\"e5\">b</p>"))
                .isEqualTo("<span>a</span><p>b</p>");
    }

    @Test
    void invalidKeyValuesAreKeptSoTheDomainCanReportThem() {
        assertThat(passage("<span class=\"w\" data-g=\"x7\">a</span><span class=\"ev\" id=\"ev4\">b</span>"))
                .isEqualTo("<span class=\"w\" data-g=\"x7\">a</span><span class=\"ev\" id=\"ev4\">b</span>");
    }

    @Test
    void textModeKeepsFormattingButNoIdsOrKeys() {
        assertThat(sanitizer.text("<p>Choose <strong>TRUE</strong></p><span class=\"ev\" id=\"e1\" data-g=\"g1\">x</span><script>x</script>").html())
                .isEqualTo("<p>Choose <strong>TRUE</strong></p><span>x</span>");
    }

    @Test
    void lettersStayAsTheyAreAndSpecialCharactersAreEscaped() {
        assertThat(passage("<p>Azərbaycanca: məhv edilmək — “quotes” 5 &lt; 6 &amp; 7</p>"))
                .isEqualTo("<p>Azərbaycanca: məhv edilmək — “quotes” 5 &lt; 6 &amp; 7</p>");
        assertThat(passage("<p>a&nbsp;b</p>")).isEqualTo("<p>a&nbsp;b</p>");
    }

    @Test
    void sanitizingTwiceChangesNothingAndEmptyInputIsEmpty() {
        String once = passage("<div><p onclick=x>a <span class=\"w q\" data-g=\"g1\">b</span></p><script>c</script>");
        assertThat(passage(once)).isEqualTo(once);
        assertThat(passage(null)).isEmpty();
        assertThat(passage("")).isEmpty();
        assertThat(sanitizer.text(null).html()).isEmpty();
    }

    // ------------------------------------------------------------ inspector

    @Test
    void theInspectorReadsKeysInOrderAsWrittenWithTheWords() {
        MarkupKeys keys = inspector.inspect(sanitizer.passage(
                "<span class=\"ev\" id=\"e2\">The <span class=\"w\" data-g=\"g7\">elk</span></span> and "
                        + "<span class=\"w\" data-g=\"g1\"> <b>willows</b> </span> <span class=\"ev\" id=\"e1\">x</span> "
                        + "<span class=\"w\" data-g=\"g7\">again</span> <span class=\"w\">no key</span> <span class=\"ev\">no id</span>"));

        assertThat(keys.glosses()).containsExactly(new MarkupKeys.GlossMark("g7", "elk"),
                new MarkupKeys.GlossMark("g1", "willows"), new MarkupKeys.GlossMark("g7", "again"));
        assertThat(keys.evidence()).containsExactly("e2", "e1");
    }

    @Test
    void aPassageWithoutAidsHasNoKeys() {
        assertThat(inspector.inspect(new SanitizedHtml("<p>Plain text.</p>"))).isEqualTo(new MarkupKeys(List.of(), List.of()));
    }

    private String passage(String html) {
        return sanitizer.passage(html).html();
    }
}
