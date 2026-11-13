package com.example.hireme.ai;

/**
 * The one way other modules talk to the language model (Groq). Feature code builds the prompts;
 * this hides the HTTP client, API key, timeouts, retries and per-user rate limit.
 * <p>
 * Every call either returns a result or throws:
 * <ul>
 *     <li>{@link AiUnavailableException} (503) when AI is turned off, Groq is down, slow or rate-limiting us,
 *     or the answer can't be used. Callers that have a non-AI fallback should catch it.</li>
 *     <li>{@link AiRateLimitExceededException} (429) when this user has made too many AI calls recently.</li>
 * </ul>
 */
public interface AiAssistant {

    /**
     * The "user" for signed-out visitors (real ids start at 1). They all share one rate-limit budget, which
     * is enough for a portfolio demo and stops anyone from burning the shared Groq quota without an account.
     */
    long GUEST_USER_ID = 0L;

    // false when hireme.ai.enabled=false: every call then throws AiUnavailableException
    boolean isEnabled();

    AiStatusResponse status();

    // Free-text answer to userPrompt, following the instructions in systemPrompt.
    String generate(Long userId, String systemPrompt, String userPrompt);

    // Structured answer: the model is asked for JSON matching responseType (a record), which is parsed into it.
    <T> T generate(Long userId, String systemPrompt, String userPrompt, Class<T> responseType);

    /**
     * A conversation: the model sees the earlier turns (the last one is the user's new message), may call
     * the given tools (objects with {@code @Tool} methods) as often as it needs, and returns its final text.
     * For a structured answer, give it a tool with {@code returnDirect = true} that records the answer.
     * Counts as one call against the user's rate limit.
     */
    String converse(Long userId, String systemPrompt, java.util.List<ChatTurn> conversation, Object... tools);
}
