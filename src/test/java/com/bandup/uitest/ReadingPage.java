package com.bandup.uitest;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** /reading/{slug}: the passage, the questions, the results. */
final class ReadingPage {

    private final Ui ui;

    ReadingPage(Ui ui) {
        this.ui = ui;
    }

    ReadingPage open(String slug) {
        ui.open("/reading/" + slug);
        return waitUntilLoaded();
    }

    ReadingPage waitUntilLoaded() {
        ui.waitFor(d -> !d.findElements(By.cssSelector("#questions .q")).isEmpty());
        return this;
    }

    String title() {
        return ui.text(By.id("passageTitle"));
    }

    String stage() {
        return ui.text(By.id("stageLabel"));
    }

    int questionCount() {
        return ui.driver().findElements(By.cssSelector("#questions .q")).size();
    }

    /** Clicks a glossed word in the passage and returns the meaning the popover shows. */
    String meaningOf(String word) {
        WebElement w = ui.driver().findElements(By.cssSelector("#passage .w")).stream()
                .filter(e -> e.getText().equals(word)).findFirst().orElseThrow();
        ui.show(w);
        w.click();
        ui.pause();
        return ui.find(By.id("pop")).getText().replaceFirst("^" + word + "\\s*", "");
    }

    void chooseHelpLanguage(String language) {
        ui.choose(By.id("helpLang"), language);
        ui.waitForText(By.id("vocabTitle"), language);
    }

    /** TRUE / FALSE / NOT GIVEN (or YES / NO …) for question n. */
    void pick(int n, String value) {
        ui.click(By.xpath("//div[@id='q" + n + "']//label[normalize-space(.)='" + value + "']"));
    }

    /** Ticks option letters of a multiple-choice question. */
    void tick(int n, String... keys) {
        for (String key : keys) {
            ui.click(By.xpath("//div[@id='q" + n + "']//label[input[@value='" + key + "']]"));
        }
    }

    void write(int n, String answer) {
        ui.type(By.id("in" + n), answer);
    }

    void select(int n, String key) {
        ui.chooseValue(By.id("sel" + n), key);
    }

    String typed(int n) {
        return ui.value(By.id("in" + n));
    }

    boolean isPicked(int n, String value) {
        return ui.driver().findElement(By.cssSelector("#q" + n + " input[value='" + value + "']")).isSelected();
    }

    void submit() {
        ui.click(By.id("submit"));
        ui.waitFor(d -> ui.isShown(By.id("resultBox")));
    }

    /** "6/6" */
    String score() {
        return ui.text(By.cssSelector("#resultBox .big"));
    }

    String feedback(int n) {
        return ui.text(By.cssSelector("#q" + n + " .fb .tag"));
    }

    String explanation(int n) {
        return ui.text(By.cssSelector("#q" + n + " .why"));
    }

    boolean isCorrect(int n) {
        return ui.find(By.id("q" + n)).getAttribute("class").contains("correct");
    }

    void showInText(int n) {
        ui.click(By.cssSelector("#q" + n + " [data-ev]"));
    }

    boolean evidenceHighlighted(String id) {
        return ui.driver().findElement(By.id(id)).getAttribute("class").contains("on");
    }

    void startAgain() {
        ui.click(By.id("reset"));
        ui.waitFor(d -> !ui.isShown(By.id("resultBox")));
    }

    String message() {
        return ui.text(By.id("passage"));
    }

    List<String> headings() {
        return ui.driver().findElements(By.cssSelector(".qgroup h3")).stream().map(WebElement::getText).toList();
    }
}
