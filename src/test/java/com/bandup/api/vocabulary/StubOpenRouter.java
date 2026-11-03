package com.bandup.api.vocabulary;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A fake OpenRouter on the JDK's HTTP server (no extra tooling): answers
 * {@code /chat/completions} with a scripted card per word, counts the calls
 * and keeps every request for checks.
 */
public final class StubOpenRouter {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern WORD = Pattern.compile("WORD: ([^\\n]+)");

    public record Call(String word, String authorization, JsonNode body) {
        public String user() {
            return body.path("messages").path(1).path("content").asString();
        }

        public String system() {
            return body.path("messages").path(0).path("content").asString();
        }
    }

    private final HttpServer server;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final List<Call> requests = new CopyOnWriteArrayList<>();
    /** word, call number (1, 2, …) → the model's text; default: a valid card for the word. */
    private volatile BiFunction<String, Integer, String> script = (word, n) -> card(word, List.of(word)).toString();
    private volatile long delayMs;
    private volatile int status = 200;

    public StubOpenRouter() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.createContext("/chat/completions", exchange -> {
            JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
            String user = body.path("messages").path(1).path("content").asString();
            Matcher m = WORD.matcher(user);
            String word = m.find() ? m.group(1).strip() : "?";
            int n = calls.computeIfAbsent(word, w -> new AtomicInteger()).incrementAndGet();
            requests.add(new Call(word, exchange.getRequestHeaders().getFirst("Authorization"), body));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ObjectNode answer = JSON.createObjectNode();
            answer.putArray("choices").addObject().putObject("message").put("role", "assistant")
                    .put("content", script.apply(word, n));
            answer.putObject("usage").put("total_tokens", 321);
            byte[] bytes = JSON.writeValueAsBytes(answer);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int calls(String word) {
        AtomicInteger n = calls.get(word);
        return n == null ? 0 : n.get();
    }

    public int totalCalls() {
        return requests.size();
    }

    public List<Call> requests() {
        return requests;
    }

    public void script(BiFunction<String, Integer, String> script) {
        this.script = script;
    }

    public void delay(long ms) {
        this.delayMs = ms;
    }

    public void status(int status) {
        this.status = status;
    }

    public void reset() {
        calls.clear();
        requests.clear();
        script = (word, n) -> card(word, List.of(word)).toString();
        delayMs = 0;
        status = 200;
    }

    /** A valid card (card-format.md) for {@code lemma} with these extra forms. */
    public static ObjectNode card(String lemma, List<String> forms) {
        ObjectNode c = JSON.createObjectNode();
        c.put("status", "OK");
        c.put("lemma", lemma);
        ArrayNode f = c.putArray("forms");
        f.add(lemma);
        forms.forEach(f::add);
        c.put("pos", "noun");
        c.put("level", "B2");
        c.put("ipa", "/test/");
        c.putObject("meaning").put("en", "a thing used in the tests").put("az", "test sözü");
        c.putArray("examples").add("This sentence uses " + lemma + " once.").add("Here is " + lemma + " in a second sentence.");
        c.putArray("collocations").add("a big " + lemma);
        c.putArray("family");
        c.putObject("gap").put("sentence", "We talked about the ___ for an hour.").put("answer", lemma);
        ObjectNode pair = c.putObject("pair");
        pair.put("sentence", "She found a new ___ in the box.");
        pair.putArray("options").add(lemma).add("table").add("window").add("garden");
        pair.put("answer", 0);
        c.putArray("wrongMeanings").add("a kind of fruit").add("a small river").add("a type of music");
        return c;
    }

    public void stop() {
        server.stop(0);
    }
}
