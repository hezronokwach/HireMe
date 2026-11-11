package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Builds the AiAssistant. The chat model is created here (no Spring AI starter), and only when
 * hireme.ai.enabled=true, so the app starts without a Groq key and nothing else in Spring AI needs one.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    AiAssistant aiAssistant(AiProperties properties, Clock clock) {
        if (!properties.enabled()) {
            log.info("AI features are off (hireme.ai.enabled=false)");
            return new DisabledAiAssistant(properties.requestsPerMinute());
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "hireme.ai.enabled=true but no API key is set: set GROQ_API_KEY, or set HIREME_AI_ENABLED=false");
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(properties.apiKey())
                        .baseUrl(properties.baseUrl())
                        .model(properties.model())
                        .timeout(properties.timeout())
                        .maxRetries(properties.maxRetries())
                        .maxTokens(properties.maxTokens())
                        // Groq rejects `reasoning_content` on earlier assistant messages, which Spring AI would
                        // otherwise send back after a tool call (reasoning models like gpt-oss return it).
                        .replayReasoningContent(false)
                        .build())
                .build();
        log.info("AI features are on: model {} at {}", properties.model(), properties.baseUrl());
        return new ChatClientAiAssistant(ChatClient.create(chatModel),
                new UserRateLimiter(properties.requestsPerMinute(), clock), properties.model());
    }
}
