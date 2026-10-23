package com.bandup.uitest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What an author (admin) does, step by step, in a real Chrome. */
class AuthorScenariosTest extends UiTestBase {

    @Test
    @DisplayName("An author writes an exercise, publishes it, and a learner practises it")
    void writePublishPractise() {
        String slug = unique("beavers");
        signInAsAdmin();
        EditorPage editor = new EditorPage(ui).openNew();
        ui.step("Empty editor");

        editor.setAddress(slug);
        editor.setTitle("The Beavers Come Back");
        editor.writePassage("Beavers built new dams on the river after the wolves returned.");
        editor.markEvidence("Beavers built new dams on the river");
        editor.addGlossTo("dams");
        editor.setMeaning("g1", "az", "bəndlər");
        editor.setMeaning("g1", "en", "walls that hold back water");
        ui.step("Passage written, word meaning and evidence marked");
        editor.doneWithMeaning();

        editor.addQuestionGroup();
        editor.setInstruction(0, "Do the statements agree with the information in the text?");
        editor.setStatement(0, 0, "Beavers built dams after the wolves came back.");
        assertThat(editor.hints()).contains("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        editor.setAnswer(0, 0, "TRUE");
        editor.setEvidence(0, 0, "e1");
        editor.setExplanation(0, 0, "en", "The text says beavers built <b>new dams</b>.");
        ui.step("Question written");
        assertThat(editor.hints()).isEmpty();

        editor.save();
        ui.waitForPath("/admin/sessions/" + slug + "/edit");
        ui.waitFor(d -> editor.revision().equals("Revision 1 · version 0"));
        ui.step("Saved as a draft");

        editor.publish();
        editor.waitForStatus("published");
        ui.step("Published");

        ui.open("/reading/" + slug);
        ReadingPage reading = new ReadingPage(ui).waitUntilLoaded();
        reading.chooseHelpLanguage("Azərbaycanca");
        assertThat(reading.meaningOf("dams")).isEqualTo("bəndlər");
        reading.pick(1, "TRUE");
        reading.submit();
        ui.step("The learner's result");
        assertThat(reading.score()).isEqualTo("1/1");
    }

    @Test
    @DisplayName("Publishing an unfinished exercise shows exactly what to fix")
    void publishBlocked() {
        String slug = unique("unfinished");
        signInAsAdmin();
        EditorPage editor = new EditorPage(ui).openNew();
        editor.setAddress(slug);
        editor.setTitle("Unfinished");
        editor.writePassage("Wolves came back.");
        editor.addQuestionGroup();
        editor.setInstruction(0, "True or false?");
        editor.setStatement(0, 0, "Wolves came back.");
        editor.publish();
        ui.step("Publish refused");

        assertThat(editor.errors()).contains("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        assertThat(editor.errorAt("questionGroups[0].questions[0].answer")).isEqualTo("Q1 must be one of TRUE / FALSE / NOT GIVEN.");
        assertThat(editor.status()).isEqualTo("draft");

        editor.setAnswer(0, 0, "TRUE");
        editor.publish();
        editor.waitForStatus("published");
        ui.step("Fixed and published");
    }

    @Test
    @DisplayName("Changing a published exercise asks first and makes a new revision")
    void editPublished() {
        String slug = unique("revised");
        signInAsAdmin();
        EditorPage editor = new EditorPage(ui).openNew();
        editor.setAddress(slug);
        editor.setTitle("First version");
        editor.writePassage("Wolves came back.");
        editor.addQuestionGroup();
        editor.setInstruction(0, "True or false?");
        editor.setStatement(0, 0, "Wolves came back.");
        editor.setAnswer(0, 0, "TRUE");
        editor.publish();
        editor.waitForStatus("published");

        editor.setTitle("Second version");
        ui.step("Changing the title of a published exercise");
        editor.save();
        ui.acceptDialog(); // "Learners see this exercise. Saving creates revision 2 …"
        ui.waitFor(d -> editor.revision().startsWith("Revision 2"));
        ui.step("Revision 2 saved");
    }

    @Test
    @DisplayName("Deleting an exercise learners have practised archives it instead")
    void deletePractised() {
        String slug = unique("practised");
        signInAsAdmin();
        EditorPage editor = new EditorPage(ui).openNew();
        editor.setAddress(slug);
        editor.setTitle("Practised exercise");
        editor.writePassage("Wolves came back.");
        editor.addQuestionGroup();
        editor.setInstruction(0, "True or false?");
        editor.setStatement(0, 0, "Wolves came back.");
        editor.setAnswer(0, 0, "TRUE");
        editor.publish();
        editor.waitForStatus("published");

        ReadingPage reading = new ReadingPage(ui).open(slug);
        reading.pick(1, "TRUE");
        reading.submit();
        ui.step("A learner practised it");

        AdminListPage list = new AdminListPage(ui).open();
        list.delete(slug);
        ui.step("Deleted from the list");
        assertThat(list.note()).contains("archived instead of deleted");
        assertThat(list.status(slug)).isEqualTo("archived");
        assertThat(list.hasEditButton(slug)).isFalse();
    }

    @Test
    @DisplayName("A learner can't get into the admin area")
    void learnerKeptOut() {
        ui.open("/register");
        RegisterPage register = new RegisterPage(ui);
        register.fillIn("Learner", unique("learner") + "@bandup.test", PASSWORD, "English");
        register.submit();
        ui.waitForPath("/");

        ui.open("/admin/sessions");
        ui.waitFor(d -> ui.isShown(org.openqa.selenium.By.cssSelector("[data-admin-noaccess]")));
        ui.step("No access for learners");
    }

    @Test
    @DisplayName("Signed-out visitors are sent to sign in and brought back to the admin area")
    void signInToAdmin() {
        ui.open("/admin/sessions");
        ui.waitForPath("/login");
        ui.step("Sent to sign in");
        new LoginPage(ui).signIn(ADMIN_EMAIL, ADMIN_PASSWORD);
        ui.waitForPath("/admin/sessions");
        new AdminListPage(ui).open();
        ui.step("In the admin area");
    }

    private void signInAsAdmin() {
        new LoginPage(ui).open().signIn(ADMIN_EMAIL, ADMIN_PASSWORD);
        ui.waitForPath("/");
    }
}
