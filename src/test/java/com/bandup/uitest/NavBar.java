package com.bandup.uitest;

import org.openqa.selenium.By;

/** The top navigation bar of every learner page. */
final class NavBar {

    private final Ui ui;

    NavBar(Ui ui) {
        this.ui = ui;
    }

    void clickSignIn() {
        ui.click(By.cssSelector("[data-nav-signin] a"));
    }

    void clickSignOut() {
        ui.click(By.cssSelector("[data-nav-signout]"));
    }

    /** The signed-in user's name; waits until the bar shows it. */
    String userName() {
        ui.waitFor(d -> !ui.text(By.cssSelector("[data-nav-username]")).isEmpty());
        return ui.text(By.cssSelector("[data-nav-username]"));
    }

    boolean showsSignIn() {
        ui.waitFor(d -> ui.isShown(By.cssSelector("[data-nav-signin]")) || ui.isShown(By.cssSelector("[data-nav-account]")));
        return ui.isShown(By.cssSelector("[data-nav-signin]"));
    }

    boolean showsAdminLink() {
        return ui.isShown(By.cssSelector("[data-nav-admin]"));
    }
}
