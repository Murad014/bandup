package com.bandup.uitest;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

/** /admin/sessions/new and /admin/sessions/{slug}/edit: the exercise editor. */
final class EditorPage {

    private final Ui ui;

    EditorPage(Ui ui) {
        this.ui = ui;
    }

    EditorPage openNew() {
        ui.open("/admin/sessions/new");
        ui.find(By.id("slug"));
        return this;
    }

    EditorPage open(String slug) {
        ui.open("/admin/sessions/" + slug + "/edit");
        ui.waitFor(d -> !ui.value(By.id("title")).isEmpty());
        return this;
    }

    private static By field(String path) {
        return By.cssSelector("[data-field='" + path + "']");
    }

    void setAddress(String slug) {
        ui.type(By.id("slug"), slug);
    }

    void setTitle(String title) {
        ui.type(By.id("title"), title);
    }

    /** Types the passage into the editor, like an author would. */
    void writePassage(String text) {
        WebElement editor = ui.find(By.id("passageEdit"));
        ui.show(editor);
        editor.click();
        Keys selectAll = System.getProperty("os.name").toLowerCase().contains("mac") ? Keys.COMMAND : Keys.CONTROL;
        editor.sendKeys(Keys.chord(selectAll, "a"), Keys.DELETE);
        editor.sendKeys(text);
        ui.pause();
    }

    /** Selects the given words in the passage editor (as with the mouse). */
    void selectInPassage(String words) {
        ui.script("const root = document.getElementById('passageEdit');"
                + "const walk = document.createTreeWalker(root, NodeFilter.SHOW_TEXT); let n;"
                + "while ((n = walk.nextNode())) { const i = n.data.indexOf(arguments[0]); if (i >= 0) {"
                + " const r = document.createRange(); r.setStart(n, i); r.setEnd(n, i + arguments[0].length);"
                + " const s = getSelection(); s.removeAllRanges(); s.addRange(r); return; } }", words);
        ui.pause();
    }

    void addGlossTo(String word) {
        selectInPassage(word);
        ui.click(By.id("glossBtn"));
    }

    void markEvidence(String sentence) {
        selectInPassage(sentence);
        ui.click(By.id("evidenceBtn"));
    }

    /** Types a meaning in the word's mini panel (opening it from "Word meanings" when it's closed). */
    void setMeaning(String glossKey, String languageCode, String meaning) {
        if (ui.driver().findElements(By.cssSelector("#glossPanel [data-field='glosses." + glossKey + ".lemma']")).isEmpty()) {
            ui.click(By.cssSelector(".gloss-row[data-key='" + glossKey + "'] button"));
        }
        ui.type(By.cssSelector("#glossPanel [data-field='glosses." + glossKey + ".meanings." + languageCode + "'] input"), meaning);
    }

    /** Closes the word's mini panel with "Done". */
    void doneWithMeaning() {
        ui.click(By.xpath("//div[@id='glossPanel']//button[normalize-space(.)='Done']"));
    }

    void addQuestionGroup() {
        ui.click(By.xpath("//button[normalize-space(.)='+ Add question group']"));
    }

    void setQuestionType(int group, String typeLabel) {
        ui.choose(By.cssSelector("[data-field='questionGroups[" + group + "].type'] select"), typeLabel);
    }

    void setInstruction(int group, String text) {
        ui.type(By.cssSelector("[data-field='questionGroups[" + group + "].instruction'] input"), text);
    }

    void setStatement(int group, int question, String text) {
        ui.type(By.cssSelector("[data-field='questionGroups[" + group + "].questions[" + question + "].stem'] input"), text);
    }

    void setAnswer(int group, int question, String answer) {
        ui.choose(By.cssSelector("[data-field='questionGroups[" + group + "].questions[" + question + "].answer'] select"), answer);
    }

    void setEvidence(int group, int question, String evidenceId) {
        ui.chooseValue(By.cssSelector("[data-field='questionGroups[" + group + "].questions[" + question + "].evidenceId'] select"), evidenceId);
    }

    void setExplanation(int group, int question, String languageCode, String html) {
        ui.type(By.cssSelector("[data-field='questionGroups[" + group + "].questions[" + question + "].explanations." + languageCode + "'] input"), html);
    }

    String hints() {
        return ui.isShown(By.id("publishHints")) ? ui.text(By.id("publishHints")) : "";
    }

    void save() {
        ui.click(By.id("saveBtn"));
    }

    void publish() {
        ui.click(By.id("publishBtn"));
    }

    /** "draft", "published" or "archived" (the badge shows it in capitals). */
    String status() {
        return ui.text(By.id("statusBadge")).toLowerCase();
    }

    String revision() {
        return ui.text(By.id("revisionInfo"));
    }

    void waitForStatus(String status) {
        ui.waitFor(d -> status().equals(status));
    }

    String errors() {
        ui.waitFor(d -> ui.isShown(By.id("editorErrors")));
        return ui.text(By.id("editorErrors"));
    }

    String errorAt(String path) {
        return ui.find(field(path)).findElement(By.cssSelector(".field-err")).getText();
    }
}
