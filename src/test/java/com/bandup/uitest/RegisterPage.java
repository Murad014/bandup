package com.bandup.uitest;

import org.openqa.selenium.By;

/** /register */
final class RegisterPage {

    private final Ui ui;

    RegisterPage(Ui ui) {
        this.ui = ui;
    }

    void fillIn(String name, String email, String password, String helpLanguage) {
        ui.type(By.id("displayName"), name);
        ui.type(By.id("email"), email);
        ui.type(By.id("password"), password);
        ui.choose(By.id("supportLanguage"), helpLanguage);
    }

    void submit() {
        ui.click(By.cssSelector("[data-register-form] button[type=submit]"));
    }

    String errorUnder(String field) {
        By error = By.cssSelector("[data-error-for='" + field + "']");
        ui.waitFor(d -> !ui.driver().findElement(error).getText().isEmpty());
        return ui.text(error);
    }
}
