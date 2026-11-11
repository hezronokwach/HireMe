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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        // The request is sent when the content is read, so retries wrap both.
        Supplier<String> ask = () -> chatClient.prompt().system(systemPrompt).messages(messages).tools(tools).call().content();
        return call(userId, () -> {
            for (int attempt = 1; ; attempt++) {
                try {
                    return ask.get();
                } catch (RuntimeException e) {
                    long wait = retryAfterMillis(e);
                    if (wait < 0 || attempt > MAX_CONVERSE_RETRIES) {
                        throw e;
                    }
                    log.info("Asking the model again in {} ms ({})", wait, e.getMessage());
                    sleep(wait);
                }
            }
        });
    }

    static final int MAX_CONVERSE_RETRIES = 2;
    private static final long MAX_WAIT_MILLIS = 5_000;
    private static final Pattern TRY_AGAIN = Pattern.compile("try again in ([0-9.]+)(ms|s)");

    /**
     * How long to wait before asking again, or -1 to give up. Two failures are worth a second try:
     * <ul>
     *     <li>Groq rejects a call to a tool that doesn't exist (gpt-oss now and then calls one named "json");
     *     asked again, it almost always answers properly.</li>
     *     <li>Groq's per-minute token limit (tight on the free tier, and a tool round trip sends the whole
     *     prompt again) says when to try again; waits of up to 5 seconds are worth it.</li>
     * </ul>
     */
    static long retryAfterMillis(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("tool call validation failed")) {
                return 0;
            }
            if (message.contains("rate limit reached")) {
                Matcher m = TRY_AGAIN.matcher(message);
                if (m.find()) {
                    double amount = Double.parseDouble(m.group(1));
                    long millis = (long) Math.ceil(m.group(2).equals("s") ? amount * 1000 : amount) + 100;
                    return millis <= MAX_WAIT_MILLIS ? millis : -1;
                }
                return -1;
            }
        }
        return -1;
    }

    static boolean isGroqRateLimit(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains("rate limit reached")) {
                return true;
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to ask the model again", e);
        }
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
            throw isGroqRateLimit(e) ? AiUnavailableException.busy(e) : new AiUnavailableException(e);
        }
        if (result == null || (result instanceof String text && text.isBlank())) {
            log.warn("AI call returned an empty answer");
            throw new AiUnavailableException();
        }
        return result;
    }
}
