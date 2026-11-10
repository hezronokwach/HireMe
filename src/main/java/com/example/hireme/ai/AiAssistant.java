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

    // false when hireme.ai.enabled=false: every call then throws AiUnavailableException
    boolean isEnabled();

    AiStatusResponse status();

    // Free-text answer to userPrompt, following the instructions in systemPrompt.
    String generate(Long userId, String systemPrompt, String userPrompt);

    // Structured answer: the model is asked for JSON matching responseType (a record), which is parsed into it.
    <T> T generate(Long userId, String systemPrompt, String userPrompt, Class<T> responseType);
}
