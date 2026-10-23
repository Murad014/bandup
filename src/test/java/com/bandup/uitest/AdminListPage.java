package com.bandup.uitest;

import org.openqa.selenium.By;

/** /admin/sessions: the list of exercises. */
final class AdminListPage {

    private final Ui ui;

    AdminListPage(Ui ui) {
        this.ui = ui;
    }

    AdminListPage open() {
        ui.open("/admin/sessions");
        ui.waitFor(d -> !d.findElements(By.cssSelector("tr[data-id]")).isEmpty());
        return this;
    }

    private By row(String slug) {
        return By.cssSelector("tr[data-id='" + slug + "']");
    }

    String status(String slug) {
        return ui.find(row(slug)).findElement(By.cssSelector(".badge")).getText().toLowerCase();
    }

    boolean hasEditButton(String slug) {
        return !ui.find(row(slug)).findElements(By.linkText("Edit")).isEmpty();
    }

    void delete(String slug) {
        ui.click(By.cssSelector("tr[data-id='" + slug + "'] button[data-action=delete]"));
        ui.acceptDialog();
    }

    String note() {
        ui.waitFor(d -> ui.isShown(By.id("sessionsNote")));
        return ui.text(By.id("sessionsNote"));
    }
}
