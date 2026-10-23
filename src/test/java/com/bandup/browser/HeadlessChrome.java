package com.bandup.browser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A headless Chrome driven over the DevTools protocol with the JDK's own
 * HTTP/WebSocket client, so browser tests need no Node, npm or extra
 * library. One instance = one browser with a fresh profile; {@link #newTab}
 * opens tabs that share its cookies (like a real browser).
 *
 * <p>{@link #find()} returns null when Chrome isn't installed; tests then
 * skip themselves ({@code CHROME_BIN} overrides the location).
 */
final class HeadlessChrome implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> LOCATIONS = List.of(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser");

    private final Process process;
    private final Path profile;
    private final int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HeadlessChrome(Process process, Path profile, int port) {
        this.process = process;
        this.profile = profile;
        this.port = port;
    }

    /** The Chrome binary, or null if there is none. */
    static String find() {
        String env = System.getenv("CHROME_BIN");
        if (env != null && Files.isExecutable(Path.of(env))) {
            return env;
        }
        return LOCATIONS.stream().filter(p -> Files.isExecutable(Path.of(p))).findFirst().orElse(null);
    }

    static HeadlessChrome start() throws Exception {
        Path profile = Files.createTempDirectory("bandup-chrome");
        Process process = new ProcessBuilder(find(), "--headless=new", "--disable-gpu", "--no-first-run",
                "--no-default-browser-check", "--remote-debugging-port=0", "--user-data-dir=" + profile,
                "--window-size=1280,900", "about:blank")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        Path portFile = profile.resolve("DevToolsActivePort");
        long deadline = System.currentTimeMillis() + 60_000; // a fresh profile right after another Chrome quit can be slow
        while (!Files.exists(portFile) || Files.readAllLines(portFile).isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                process.destroyForcibly();
                throw new IllegalStateException("Chrome didn't start");
            }
            Thread.sleep(100);
        }
        return new HeadlessChrome(process, profile, Integer.parseInt(Files.readAllLines(portFile).getFirst().strip()));
    }

    /** A new tab showing {@code url}, ready to be driven. */
    Tab newTab(String url) throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/json/new?about:blank"))
                .PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        String ws = JSON.readTree(res.body()).get("webSocketDebuggerUrl").asString();
        Tab tab = new Tab(http, ws);
        tab.call("Page.enable", JSON.createObjectNode());
        tab.call("Runtime.enable", JSON.createObjectNode());
        // Tests don't depend on the internet: the pages' Google Fonts are blocked (system fonts are used).
        tab.call("Network.enable", JSON.createObjectNode());
        ObjectNode blocked = JSON.createObjectNode();
        blocked.putArray("urls").add("*fonts.googleapis.com*").add("*fonts.gstatic.com*");
        tab.call("Network.setBlockedURLs", blocked);
        tab.navigate(url); // a new tab reports "complete" for about:blank first; wait for the real page
        return tab;
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
            try (Stream<Path> files = Files.walk(profile)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** One tab: navigate, run JavaScript, wait for a condition. */
    static final class Tab {

        private final WebSocket socket;
        private final AtomicInteger ids = new AtomicInteger();
        private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

        Tab(HttpClient http, String url) {
            this.socket = http.newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
                private final StringBuilder buffer = new StringBuilder();

                @Override
                public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (last) {
                        JsonNode message = JSON.readTree(buffer.toString());
                        buffer.setLength(0);
                        if (message.has("id")) {
                            CompletableFuture<JsonNode> future = pending.remove(message.get("id").asInt());
                            if (future != null) {
                                future.complete(message);
                            }
                        }
                    }
                    ws.request(1);
                    return null;
                }
            }).join();
        }

        JsonNode call(String method, ObjectNode params) throws Exception {
            int id = ids.incrementAndGet();
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pending.put(id, future);
            ObjectNode message = JSON.createObjectNode().put("id", id).put("method", method);
            message.set("params", params);
            socket.sendText(message.toString(), true).join();
            JsonNode reply = future.get(30, TimeUnit.SECONDS);
            if (reply.has("error")) {
                throw new IllegalStateException(method + ": " + reply.get("error"));
            }
            return reply.get("result");
        }

        /** Opens {@code url} and waits until it has loaded. */
        void navigate(String url) throws Exception {
            call("Page.navigate", JSON.createObjectNode().put("url", url));
            waitFor("location.href === " + JSON.writeValueAsString(url) + " && document.readyState === 'complete'",
                    Duration.ofSeconds(15));
        }

        /** Saves a PNG of the whole page to {@code file} (for looking at a result). */
        void screenshot(Path file) throws Exception {
            JsonNode metrics = call("Page.getLayoutMetrics", JSON.createObjectNode());
            ObjectNode clip = JSON.createObjectNode().put("x", 0).put("y", 0).put("scale", 1)
                    .put("width", metrics.get("cssContentSize").get("width").asDouble())
                    .put("height", Math.min(4000, metrics.get("cssContentSize").get("height").asDouble()));
            ObjectNode params = JSON.createObjectNode().put("format", "png").put("captureBeyondViewport", true);
            params.set("clip", clip);
            String data = call("Page.captureScreenshot", params).get("data").asString();
            Files.createDirectories(file.getParent());
            Files.write(file, java.util.Base64.getDecoder().decode(data));
        }

        /** Starts loading {@code url} without waiting (for pages that redirect away); follow with waitFor. */
        void open(String url) throws Exception {
            call("Page.navigate", JSON.createObjectNode().put("url", url));
        }

        /** Runs {@code expression} (promises are awaited) and returns its JSON value; JS errors fail the test. */
        JsonNode eval(String expression) throws Exception {
            JsonNode result = call("Runtime.evaluate", JSON.createObjectNode().put("expression", expression)
                    .put("awaitPromise", true).put("returnByValue", true));
            if (result.has("exceptionDetails")) {
                throw new AssertionError("JavaScript failed: " + result.get("exceptionDetails"));
            }
            JsonNode value = result.get("result").get("value");
            return value == null ? JSON.nullNode() : value;
        }

        String text(String expression) throws Exception {
            JsonNode value = eval(expression);
            return value.isNull() ? null : value.asString();
        }

        /** Waits until the JavaScript condition is true. */
        void waitFor(String condition, Duration timeout) throws Exception {
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (true) {
                try {
                    if (eval("!!(" + condition + ")").asBoolean()) {
                        return;
                    }
                } catch (AssertionError | IllegalStateException e) {
                    // the page may be between documents; try again
                }
                if (System.currentTimeMillis() > deadline) {
                    throw new AssertionError("Timed out waiting for: " + condition);
                }
                Thread.sleep(100);
            }
        }

        void waitFor(String condition) throws Exception {
            waitFor(condition, Duration.ofSeconds(10));
        }
    }
}
