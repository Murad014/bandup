package com.bandup.uitest;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.bandup.TestcontainersConfiguration;

/**
 * Starts the real app (random port, its own PostgreSQL in Docker) and a real
 * Chrome for each scenario. Run with {@code sh gradlew uiTest} to watch a
 * visible Chrome, or add {@code -Pheadless}. The first admin comes from the
 * same settings as in production ({@code bandup.auth.admin-*}).
 */
@Tag("ui")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bandup.auth.admin-email=" + UiTestBase.ADMIN_EMAIL,
        "bandup.auth.admin-password=" + UiTestBase.ADMIN_PASSWORD})
@Import(TestcontainersConfiguration.class)
abstract class UiTestBase {

    static final String ADMIN_EMAIL = "admin@bandup.test";
    static final String ADMIN_PASSWORD = "admin-password-123";
    static final String PASSWORD = "correct horse battery";

    @LocalServerPort
    int port;

    ChromeDriver driver;
    Ui ui;

    @BeforeEach
    void openChrome(TestInfo test) {
        boolean headless = Boolean.getBoolean("ui.headless");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1366,900", "--no-first-run", "--no-default-browser-check",
                "--disable-search-engine-choice-screen");
        if (headless) {
            options.addArguments("--headless=new");
        }
        // Leave dialogs (confirm / alert) to the scenario; Selenium would otherwise dismiss them on the next command.
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        try {
            driver = new ChromeDriver(options);
        } catch (RuntimeException e) {
            assumeTrue(false, "Chrome or ChromeDriver isn't available: " + e.getMessage());
        }
        // The scenarios don't depend on the internet: the pages' Google Fonts are blocked.
        driver.executeCdpCommand("Network.enable", Map.of());
        driver.executeCdpCommand("Network.setBlockedURLs", Map.of("urls", List.of("*fonts.googleapis.com*", "*fonts.gstatic.com*")));
        long slow = headless ? 0 : Long.parseLong(System.getProperty("ui.slowMs", "700"));
        String name = test.getTestMethod().map(m -> m.getName()).orElse("test");
        Path shots = Path.of("build", "ui-screenshots", getClass().getSimpleName(), name);
        ui = new Ui(driver, "http://localhost:" + port, slow, shots);
        System.out.println("SCENARIO " + getClass().getSimpleName() + " › " + test.getDisplayName());
    }

    @AfterEach
    void closeChrome() {
        if (driver != null) {
            ui.pause();
            driver.quit();
        }
    }

    static String unique(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
