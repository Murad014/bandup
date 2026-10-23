package com.bandup.uitest;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** /reading: the catalog of reading exercises. */
final class CatalogPage {

    private final Ui ui;

    CatalogPage(Ui ui) {
        this.ui = ui;
    }

    CatalogPage open() {
        ui.open("/reading");
        return waitForCards();
    }

    CatalogPage waitForCards() {
        ui.waitFor(d -> !d.findElements(By.cssSelector(".ex-card")).isEmpty() || ui.isShown(By.id("catalogStatus")));
        return this;
    }

    List<String> titles() {
        return ui.driver().findElements(By.cssSelector(".ex-card h2")).stream().map(WebElement::getText).toList();
    }

    String cardText(String title) {
        return card(title).getText();
    }

    private WebElement card(String title) {
        return ui.find(By.xpath("//a[contains(@class,'ex-card')][h2[normalize-space(.)='" + title + "']]"));
    }

    /** Clicks a filter chip (level, question type or topic) by its label. */
    void toggle(String chipLabel) {
        String before = ui.path();
        ui.click(By.xpath("//label[contains(@class,'chip')][span[normalize-space(.)='" + chipLabel + "']]"));
        ui.waitFor(d -> !ui.path().equals(before));
        waitForCards();
    }

    void hideDone() {
        ui.click(By.id("notDoneFilter"));
        ui.waitFor(d -> ui.path().contains("notDone=true"));
        waitForCards();
    }

    void clearFilters() {
        ui.click(By.id("clearFilters"));
        ui.waitForPath("/reading");
        waitForCards();
    }

    void open(String title) {
        ui.show(card(title));
        card(title).click();
    }

    String status() {
        return ui.text(By.id("catalogStatus"));
    }
}
