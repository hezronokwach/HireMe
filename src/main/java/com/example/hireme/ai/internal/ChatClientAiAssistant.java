package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiStatusResponse;
import com.example.hireme.ai.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.hireme.ai.ChatTurn;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Calls Groq through Spring AI's ChatClient. Any failure from the model (HTTP error, timeout, retries
 * used up, empty or unparsable answer) becomes {@link AiUnavailableException}, so callers only ever
 * see a result, a 503 or a 429.
 */
class ChatClientAiAssistant implements AiAssistant {

    private static final Logger log = LoggerFactory.getLogger(ChatClientAiAssistant.class);

    private final ChatClient chatClient;
    private final UserRateLimiter rateLimiter;
    private final String model;

    ChatClientAiAssistant(ChatClient chatClient, UserRateLimiter rateLimiter, String model) {
        this.chatClient = chatClient;
        this.rateLimiter = rateLimiter;
        this.model = model;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public AiStatusResponse status() {
        return new AiStatusResponse(true, model, rateLimiter.limit());
    }

    @Override
    public String generate(Long userId, String systemPrompt, String userPrompt) {
        return call(userId, systemPrompt, userPrompt, ChatClient.CallResponseSpec::content);
    }

    @Override
    public <T> T generate(Long userId, String systemPrompt, String userPrompt, Class<T> responseType) {
        return call(userId, systemPrompt, userPrompt, response -> response.entity(responseType));
    }

    @Override
    public String converse(Long userId, String systemPrompt, List<ChatTurn> conversation, Object... tools) {
        List<Message> messages = conversation.stream()
                .<Message>map(turn -> turn.role() == ChatTurn.Role.USER
                        ? new UserMessage(turn.text())
                        : new AssistantMessage(turn.text()))
                .toList();
        // Plain text, not entity(): asked for JSON while it has tools, gpt-oss tries to call a "json" tool.
        // It still does that now and then; Groq rejects such a call, and a second try almost always works.
        // (The request is sent when the content is read, so the retry wraps both.)
        Supplier<String> ask = () -> chatClient.prompt().system(systemPrompt).messages(messages).tools(tools).call().content();
        return call(userId, () -> {
            try {
                return ask.get();
            } catch (RuntimeException e) {
                if (!isInvalidToolCall(e)) {
                    throw e;
                }
                log.info("The model called a tool that doesn't exist; asking once more");
                return ask.get();
            }
        });
    }

    static boolean isInvalidToolCall(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(java.util.Locale.ROOT).contains("tool call validation failed")) {
                return true;
            }
        }
        return false;
    }

    private <T> T call(Long userId, String systemPrompt, String userPrompt, Function<ChatClient.CallResponseSpec, T> read) {
        return call(userId, () -> read.apply(chatClient.prompt().system(systemPrompt).user(userPrompt).call()));
    }

    private <T> T call(Long userId, Supplier<T> work) {
        rateLimiter.acquire(userId); // 429 before spending any Groq quota
        T result;
        try {
            result = work.get();
        } catch (RuntimeException e) {
            log.warn("AI call failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new AiUnavailableException(e);
        }
        if (result == null || (result instanceof String text && text.isBlank())) {
            log.warn("AI call returned an empty answer");
            throw new AiUnavailableException();
        }
        return result;
    }
}
