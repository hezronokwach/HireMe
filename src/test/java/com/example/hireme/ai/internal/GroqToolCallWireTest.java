package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.ai.ChatTurn;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conversations with tools through the real client built by AiConfig, against a fake Groq that plays
 * scripted answers: a tool call, then a final answer. Checks the parts Groq is strict about.
 */
class GroqToolCallWireTest {

    record Reply(int status, String body) {
    }

    // A tool the model can call; counts its calls.
    static class PriceTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool(description = "Price of a listing")
        public String price(Long listingId) {
            calls.incrementAndGet();
            return "KES 6000 for listing " + listingId;
        }
    }

    private final JsonMapper json = JsonMapper.builder().build();
    private final Queue<Reply> script = new ConcurrentLinkedQueue<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void startFakeGroq() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(json.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply = script.poll();
            if (reply == null) {
                reply = new Reply(500, "{\"error\":{\"message\":\"no more scripted answers\"}}");
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
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

    private AiAssistant assistant() {
        AiProperties properties = new AiProperties(true, "test-key",
                "http://localhost:" + server.getAddress().getPort() + "/openai/v1",
                "openai/gpt-oss-120b", Duration.ofSeconds(10), 0, 256, 10);
        return new AiConfig().aiAssistant(properties, Clock.systemUTC());
    }

    private String completion(ObjectNode message, String finishReason) {
        return json.writeValueAsString(json.createObjectNode()
                .put("id", "chatcmpl-test").put("object", "chat.completion").put("created", 1).put("model", "openai/gpt-oss-120b")
                .set("choices", json.createArrayNode().add(json.createObjectNode()
                        .put("index", 0).put("finish_reason", finishReason).set("message", message))));
    }

    private Reply toolCall() {
        ObjectNode message = json.createObjectNode().put("role", "assistant")
                .put("reasoning_content", "The user wants a price, so I call the tool.");
        message.putNull("content");
        message.set("tool_calls", json.createArrayNode().add(json.createObjectNode()
                .put("id", "call_1").put("type", "function")
                .set("function", json.createObjectNode().put("name", "price").put("arguments", "{\"listingId\":7}"))));
        return new Reply(200, completion(message, "tool_calls"));
    }

    private Reply answer(String text) {
        return new Reply(200, completion(json.createObjectNode().put("role", "assistant").put("content", text), "stop"));
    }

    @Test
    void sendsTheConversation_runsTheTool_andSendsItsResultBack() {
        script.add(toolCall());
        script.add(answer("It costs KES 6,000 a day."));
        PriceTool tool = new PriceTool();

        String text = assistant().converse(1L, "You help hire equipment.",
                List.of(ChatTurn.user("I need a tractor"), ChatTurn.assistant("Which one?"), ChatTurn.user("the first, price?")),
                tool);

        assertEquals("It costs KES 6,000 a day.", text);
        assertEquals(1, tool.calls.get());
        assertEquals(2, requests.size());

        JsonNode first = requests.getFirst();
        assertEquals("system", first.at("/messages/0/role").asString());
        assertEquals("user", first.at("/messages/1/role").asString());
        assertEquals("assistant", first.at("/messages/2/role").asString());
        assertEquals("Which one?", first.at("/messages/2/content").asString());
        assertEquals("price", first.at("/tools/0/function/name").asString(), "the tool is offered");

        JsonNode second = requests.get(1);
        JsonNode messages = second.get("messages");
        JsonNode toolCallMessage = messages.get(messages.size() - 2);
        JsonNode toolResult = messages.get(messages.size() - 1);
        assertEquals("assistant", toolCallMessage.get("role").asString());
        assertFalse(toolCallMessage.has("reasoning_content"), "Groq rejects reasoning_content on earlier messages");
        assertEquals("tool", toolResult.get("role").asString());
        assertTrue(toolResult.get("content").asString().contains("KES 6000 for listing 7"));
    }

    @Test
    void aCallToAToolThatDoesNotExist_isAskedOnceMore() {
        script.add(new Reply(400, "{\"error\":{\"message\":\"Tool call validation failed: attempted to call tool 'json' "
                + "which was not in request.tools\",\"type\":\"invalid_request_error\"}}"));
        script.add(answer("Here you go."));

        assertEquals("Here you go.", assistant().converse(1L, "s", List.of(ChatTurn.user("hi")), new PriceTool()));
        assertEquals(2, requests.size());
    }

    @Test
    void otherErrors_areNotRetried_andBecomeUnavailable() {
        script.add(new Reply(400, "{\"error\":{\"message\":\"context too long\"}}"));

        assertThrows(AiUnavailableException.class,
                () -> assistant().converse(1L, "s", List.of(ChatTurn.user("hi")), new PriceTool()));
        assertEquals(1, requests.size());
    }
}
