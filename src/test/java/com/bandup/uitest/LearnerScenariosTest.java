package com.bandup.uitest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a learner does, step by step, in a real Chrome. */
class LearnerScenariosTest extends UiTestBase {

    @Test
    @DisplayName("A new learner signs up from the reading page and comes back to it, signed in")
    void signUpAndComeBack() {
        ReadingPage reading = new ReadingPage(ui).open("yellowstone-wolves");
        NavBar nav = new NavBar(ui);
        ui.step("Reading page as a visitor");
        assertThat(nav.showsSignIn()).isTrue();

        nav.clickSignIn();
        new LoginPage(ui).goToRegister();
        ui.waitForPath("/register");
        RegisterPage register = new RegisterPage(ui);
        register.fillIn("Aysel", unique("aysel") + "@bandup.test", PASSWORD, "Azərbaycanca");
        ui.step("Sign-up form filled in");
        register.submit();

        ui.waitForPath("/reading/yellowstone-wolves");
        reading.waitUntilLoaded();
        ui.step("Back on the reading page, signed in");
        assertThat(nav.userName()).isEqualTo("Aysel");
        assertThat(nav.showsAdminLink()).isFalse();
    }

    @Test
    @DisplayName("A wrong password gets one clear message")
    void wrongPassword() {
        LoginPage login = new LoginPage(ui).open();
        login.signIn("nobody@bandup.test", "not-the-password");
        ui.step("Wrong password");
        assertThat(login.errorMessage()).isEqualTo("Wrong email or password.");
    }

    @Test
    @DisplayName("A learner answers every Wolves question correctly, reads the explanations and starts again")
    void perfectAttempt() {
        ReadingPage reading = new ReadingPage(ui).open("yellowstone-wolves");
        ui.step("Wolves passage loaded");
        assertThat(reading.title()).isEqualTo("The Return of the Wolves");
        assertThat(reading.questionCount()).isEqualTo(6);
        assertThat(reading.headings()).containsExactly("Questions 1–3", "Questions 4–6");

        reading.pick(1, "FALSE");
        reading.pick(2, "TRUE");
        reading.pick(3, "NOT GIVEN");
        reading.write(4, "riverbanks");
        reading.write(5, "songbirds");
        reading.write(6, "wolves");
        ui.step("All six answered");

        reading.submit();
        ui.step("Results");
        assertThat(reading.score()).isEqualTo("6/6");
        assertThat(reading.isCorrect(1)).isTrue();
        assertThat(reading.feedback(1)).isEqualTo("✓ Correct");
        assertThat(reading.explanation(1)).contains("hunting");

        reading.showInText(1);
        ui.step("The sentence that proves answer 1 is highlighted");
        assertThat(reading.evidenceHighlighted("e1")).isTrue();

        reading.startAgain();
        ui.step("Started again");
        assertThat(reading.typed(4)).isEmpty();
    }

    @Test
    @DisplayName("A learner gets some answers wrong and sees the right ones")
    void mixedAttempt() {
        ReadingPage reading = new ReadingPage(ui).open("yellowstone-wolves");
        reading.pick(1, "TRUE");
        reading.write(4, "River banks");
        reading.submit();
        ui.step("Results with mistakes");

        assertThat(reading.score()).isEqualTo("0/6");
        assertThat(reading.feedback(1)).isEqualTo("✗ · You answered: TRUE · Answer: FALSE");
        assertThat(reading.feedback(4)).contains("You answered: River banks").contains("Answer: riverbanks");
        assertThat(reading.feedback(5)).contains("No answer");
    }

    @Test
    @DisplayName("Word meanings follow the help language, and the answers stay")
    void helpLanguage() {
        ReadingPage reading = new ReadingPage(ui).open("yellowstone-wolves");
        reading.chooseHelpLanguage("Azərbaycanca");
        ui.step("Help in Azerbaijani");
        assertThat(reading.meaningOf("eliminated")).isEqualTo("məhv edilmək");

        reading.write(4, "riverbanks");
        reading.pick(1, "FALSE");
        reading.chooseHelpLanguage("English");
        ui.step("Help in English");
        assertThat(reading.meaningOf("eliminated")).isEqualTo("removed completely");
        assertThat(reading.typed(4)).isEqualTo("riverbanks");
        assertThat(reading.isPicked(1, "FALSE")).isTrue();
    }

    @Test
    @DisplayName("A learner finds a passage in the catalog with filters, practises it, and sees it marked as done")
    void browseTheCatalog() {
        ui.open("/");
        ui.click(org.openqa.selenium.By.cssSelector("a[data-nav-link]"));
        CatalogPage catalog = new CatalogPage(ui).waitForCards();
        ui.step("The reading catalog");
        assertThat(catalog.titles()).contains("The Return of the Wolves");
        assertThat(catalog.cardText("The Return of the Wolves")).contains("B1", "5 min", "6 questions");

        catalog.toggle("B1");
        catalog.toggle("Nature");
        ui.step("Filtered: B1, Nature");
        assertThat(ui.path()).isEqualTo("/reading?level=B1&topic=nature");
        assertThat(catalog.titles()).contains("The Return of the Wolves");

        catalog.open("The Return of the Wolves");
        ReadingPage reading = new ReadingPage(ui).waitUntilLoaded();
        reading.pick(1, "FALSE");
        reading.submit();
        ui.step("Practised as a visitor");

        new NavBar(ui).clickSignIn();
        new LoginPage(ui).goToRegister();
        RegisterPage register = new RegisterPage(ui);
        register.fillIn("Browser", unique("browser") + "@bandup.test", PASSWORD, "English");
        register.submit();
        ui.waitForPath("/reading/yellowstone-wolves");
        reading.waitUntilLoaded().pick(1, "FALSE");
        reading.submit();

        new CatalogPage(ui).open();
        ui.waitFor(d -> catalog.cardText("The Return of the Wolves").contains("Done"));
        ui.step("Marked as done, with the best score");
        assertThat(catalog.cardText("The Return of the Wolves")).contains("✓ Done", "Best score 1/6");

        catalog.hideDone();
        ui.step("Done exercises hidden");
        assertThat(catalog.titles()).doesNotContain("The Return of the Wolves");
    }

    @Test
    @DisplayName("A passage that doesn't exist says so")
    void unknownPassage() {
        ui.open("/reading/no-such-passage");
        ui.waitFor(d -> new ReadingPage(ui).message().contains("isn't available"));
        ui.step("Not available");
    }
}
