package com.bandup.uitest;

import org.openqa.selenium.By;

/** /login */
final class LoginPage {

    private final Ui ui;

    LoginPage(Ui ui) {
        this.ui = ui;
    }

    LoginPage open() {
        ui.open("/login");
        return this;
    }

    void signIn(String email, String password) {
        ui.type(By.id("email"), email);
        ui.type(By.id("password"), password);
        ui.click(By.cssSelector("[data-login-form] button[type=submit]"));
    }

    String errorMessage() {
        ui.waitFor(d -> ui.isShown(By.cssSelector("[data-form-error]")));
        return ui.text(By.cssSelector("[data-form-error]"));
    }

    void goToRegister() {
        ui.click(By.cssSelector("[data-keep-next]"));
    }
}
