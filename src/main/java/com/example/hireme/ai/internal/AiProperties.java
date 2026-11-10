package com.example.hireme.ai.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code hireme.ai.*}. Groq speaks the OpenAI chat-completions API, so only the base URL
 * and key differ from OpenAI.
 *
 * @param enabled           AI calls are made only when true (and then apiKey is required)
 * @param apiKey            Groq API key, from the GROQ_API_KEY environment variable; never commit it
 * @param baseUrl           OpenAI-compatible endpoint, including /v1
 * @param model             Groq model id
 * @param timeout           per request; Groq is fast, so a slow answer usually means trouble
 * @param maxRetries        retries on transient errors (timeouts, 429, 5xx) before giving up
 * @param maxTokens         upper bound on the answer length
 * @param requestsPerMinute per user, across all AI endpoints
 */
@ConfigurationProperties("hireme.ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        Duration timeout,
        int maxRetries,
        int maxTokens,
        int requestsPerMinute
) {
    public AiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.groq.com/openai/v1";
        }
        if (model == null || model.isBlank()) {
            model = "openai/gpt-oss-120b";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
        if (maxTokens <= 0) {
            maxTokens = 1024;
        }
        if (requestsPerMinute <= 0) {
            requestsPerMinute = 10;
        }
    }
}
