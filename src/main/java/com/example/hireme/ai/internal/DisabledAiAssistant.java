package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiAssistant;
import com.example.hireme.ai.AiStatusResponse;
import com.example.hireme.ai.AiUnavailableException;
import com.example.hireme.ai.ChatTurn;

import java.util.List;

// Used when hireme.ai.enabled=false: the rest of the app works, AI calls answer 503.
class DisabledAiAssistant implements AiAssistant {

    private final int requestsPerMinute;

    DisabledAiAssistant(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AiStatusResponse status() {
        return new AiStatusResponse(false, null, requestsPerMinute);
    }

    @Override
    public String generate(Long userId, String systemPrompt, String userPrompt) {
        throw new AiUnavailableException();
    }

    @Override
    public <T> T generate(Long userId, String systemPrompt, String userPrompt, Class<T> responseType) {
        throw new AiUnavailableException();
    }

    @Override
    public String converse(Long userId, String systemPrompt, List<ChatTurn> conversation, Object... tools) {
        throw new AiUnavailableException();
    }
}
