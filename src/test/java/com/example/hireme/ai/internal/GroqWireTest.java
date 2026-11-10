package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real client built by AiConfig (Spring AI + OpenAI SDK), against a fake OpenAI-compatible server
 * standing in for Groq: checks the request we send and how responses, errors and slowness are handled,
 * without a real key or network.
 */
class GroqWireTest {

    record Suggestion(String description, int suggestedDailyRateKes) {
    }

    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();

    private HttpServer server;
    private volatile int status = 200;
    private volatile String answer = "A sturdy tractor.";
    private volatile long delayMillis = 0;

    @BeforeEach
    void startFakeGroq() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            sleep(delayMillis);
            byte[] body = (status == 200 ? completion(answer) : "{\"error\":{\"message\":\"boom\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String completion(String content) {
        return json.writeValueAsString(json.createObjectNode()
                .put("id", "chatcmpl-test").put("object", "chat.completion").put("created", 1).put("model", "llama-3.3-70b-versatile")
                .set("choices", json.createArrayNode().add(json.createObjectNode()
                        .put("index", 0).put("finish_reason", "stop")
                        .set("message", json.createObjectNode().put("role", "assistant").put("content", content)))));
    }

    private AiAssistant assistant(Duration timeout) {
        AiProperties properties = new AiProperties(true, "test-key",
                "http://localhost:" + server.getAddress().getPort() + "/openai/v1",
                "llama-3.3-70b-versatile", timeout, 0, 256, 10);
        return new AiConfig().aiAssistant(properties, Clock.systemUTC());
    }

    @Test
    void sendsAnOpenAiCompatibleRequestToGroq() {
        String result = assistant(Duration.ofSeconds(10)).generate(1L, "You write listings.", "Tractor, 2019");

        assertEquals("A sturdy tractor.", result);
        assertEquals("/openai/v1/chat/completions", path.get());
        assertEquals("Bearer test-key", authorization.get());
        JsonNode body = requestBody.get();
        assertEquals("llama-3.3-70b-versatile", body.get("model").asString());
        assertEquals("system", body.at("/messages/0/role").asString());
        assertEquals("You write listings.", body.at("/messages/0/content").asString());
        assertEquals("user", body.at("/messages/1/role").asString());
        assertEquals("Tractor, 2019", body.at("/messages/1/content").asString());
    }

    @Test
    void structuredAnswer_isParsedIntoTheRecord() {
        answer = "{\"description\":\"A sturdy tractor.\",\"suggestedDailyRateKes\":6000}";

        Suggestion suggestion = assistant(Duration.ofSeconds(10)).generate(1L, "s", "u", Suggestion.class);

        assertEquals(new Suggestion("A sturdy tractor.", 6000), suggestion);
        String sentPrompt = requestBody.get().at("/messages/1/content").asString();
        assertTrue(sentPrompt.contains("suggestedDailyRateKes"), "the expected JSON shape is sent with the prompt");
    }

    @Test
    void groqError_becomesUnavailable() {
        status = 500;

        assertThrows(AiUnavailableException.class, () -> assistant(Duration.ofSeconds(10)).generate(1L, "s", "u"));
    }

    @Test
    void groqTooSlow_becomesUnavailableWithinTheTimeout() {
        delayMillis = 5_000;
        AiAssistant assistant = assistant(Duration.ofSeconds(1));

        long start = System.nanoTime();
        assertThrows(AiUnavailableException.class, () -> assistant.generate(1L, "s", "u"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 4_000, "should give up after about the timeout");
    }

    @Test
    void enabledWithoutKey_failsAtStartupWithAClearMessage() {
        AiProperties noKey = new AiProperties(true, " ", null, null, null, 0, 0, 0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AiConfig().aiAssistant(noKey, Clock.systemUTC()));
        assertTrue(ex.getMessage().contains("GROQ_API_KEY"));
    }

    @Test
    void disabled_needsNoKey() {
        AiProperties off = new AiProperties(false, null, null, null, null, 0, 0, 0);

        assertFalse(new AiConfig().aiAssistant(off, Clock.systemUTC()).isEnabled());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
