package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiStatusResponse;
import com.example.hireme.ai.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Function;

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

    private <T> T call(Long userId, String systemPrompt, String userPrompt, Function<ChatClient.CallResponseSpec, T> read) {
        rateLimiter.acquire(userId); // 429 before spending any Groq quota
        T result;
        try {
            result = read.apply(chatClient.prompt().system(systemPrompt).user(userPrompt).call());
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
