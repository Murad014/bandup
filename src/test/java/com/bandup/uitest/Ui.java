package com.bandup.uitest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * What a person does in the browser, done visibly: every element is
 * scrolled into view and outlined before it's used, the test pauses so you
 * can follow it, and every {@link #step} saves a numbered screenshot.
 */
final class Ui {

    private final WebDriver driver;
    private final String baseUrl;
    private final long slowMs;
    private final Path screenshots;
    private int stepNumber;

    Ui(WebDriver driver, String baseUrl, long slowMs, Path screenshots) {
        this.driver = driver;
        this.baseUrl = baseUrl;
        this.slowMs = slowMs;
        this.screenshots = screenshots;
    }

    WebDriver driver() {
        return driver;
    }

    void open(String path) {
        driver.get(baseUrl + path);
        pause();
    }

    String path() {
        return driver.getCurrentUrl().replace(baseUrl, "");
    }

    /** Names what happens next in the log, and saves a screenshot of the page as it is now. */
    void step(String description) {
        stepNumber++;
        System.out.println("  STEP " + stepNumber + ": " + description);
        try {
            Files.createDirectories(screenshots);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            String name = String.format("%02d-%s.png", stepNumber, description.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
            Files.write(screenshots.resolve(name.length() > 90 ? name.substring(0, 86) + ".png" : name), png);
        } catch (Exception e) {
            System.out.println("    (no screenshot: " + e.getMessage() + ")");
        }
    }

    WebElement find(By by) {
        return waitFor(ExpectedConditions.visibilityOfElementLocated(by));
    }

    void click(By by) {
        WebElement element = waitFor(ExpectedConditions.elementToBeClickable(by));
        show(element);
        element.click();
        pause();
    }

    void type(By by, String text) {
        WebElement element = find(by);
        show(element);
        element.clear();
        element.sendKeys(text);
        pause();
    }

    void choose(By by, String visibleText) {
        WebElement element = find(by);
        show(element);
        new Select(element).selectByVisibleText(visibleText);
        pause();
    }

    void chooseValue(By by, String value) {
        WebElement element = find(by);
        show(element);
        new Select(element).selectByValue(value);
        pause();
    }

    String text(By by) {
        return find(by).getText();
    }

    String value(By by) {
        return find(by).getDomProperty("value");
    }

    boolean isShown(By by) {
        return !driver.findElements(by).isEmpty() && driver.findElement(by).isDisplayed();
    }

    <T> T waitFor(Function<? super WebDriver, T> condition) {
        return new WebDriverWait(driver, Duration.ofSeconds(15)).until(condition);
    }

    void waitForText(By by, String text) {
        waitFor(ExpectedConditions.textToBePresentInElementLocated(by, text));
    }

    void waitForPath(String path) {
        waitFor(d -> path().equals(path) || path().startsWith(path + "?"));
        pause();
    }

    void acceptDialog() {
        waitFor(ExpectedConditions.alertIsPresent());
        pause();
        driver.switchTo().alert().accept();
        pause();
    }

    Object script(String javascript, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(javascript, args);
    }

    /** Scrolls the element into view and outlines it for a moment, so a watcher sees what's about to be used. */
    void show(WebElement element) {
        script("arguments[0].scrollIntoView({block: 'center'});"
                + "arguments[0].dataset.uiOutline = arguments[0].style.outline;"
                + "arguments[0].style.outline = '3px solid #ff2d55'; arguments[0].style.outlineOffset = '2px';", element);
        pause();
        script("arguments[0].style.outline = arguments[0].dataset.uiOutline || '';", element);
    }

    void pause() {
        if (slowMs > 0) {
            try {
                Thread.sleep(slowMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
