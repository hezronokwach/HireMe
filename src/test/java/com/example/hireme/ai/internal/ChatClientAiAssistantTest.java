package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiRateLimitExceededException;
import com.example.hireme.ai.AiStatusResponse;
import com.example.hireme.ai.AiUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ChatClientAiAssistantTest {

    record Suggestion(String description) {
    }

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final ChatClientAiAssistant assistant =
            new ChatClientAiAssistant(chatClient, new UserRateLimiter(2, Clock.systemUTC()), "test-model");

    private ChatClient.CallResponseSpec call() {
        return chatClient.prompt().system(anyString()).user(anyString()).call();
    }

    @Test
    void generate_returnsTheModelsText() {
        when(call().content()).thenReturn("A sturdy tractor.");

        assertEquals("A sturdy tractor.", assistant.generate(1L, "You write listings.", "Tractor, 2019"));
    }

    @Test
    void generate_structured_returnsParsedRecord() {
        when(call().entity(Suggestion.class)).thenReturn(new Suggestion("A sturdy tractor."));

        assertEquals(new Suggestion("A sturdy tractor."),
                assistant.generate(1L, "You write listings.", "Tractor", Suggestion.class));
    }

    @Test
    void modelError_becomesUnavailable() {
        when(call().content()).thenThrow(new IllegalStateException("401 invalid api key"));

        AiUnavailableException ex = assertThrows(AiUnavailableException.class, () -> assistant.generate(1L, "s", "u"));
        assertEquals(AiUnavailableException.MESSAGE, ex.getMessage());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void unparsableStructuredAnswer_becomesUnavailable() {
        when(call().entity(Suggestion.class)).thenThrow(new RuntimeException("not JSON"));

        assertThrows(AiUnavailableException.class, () -> assistant.generate(1L, "s", "u", Suggestion.class));
    }

    @Test
    void emptyAnswer_becomesUnavailable() {
        when(call().content()).thenReturn("  ");

        assertThrows(AiUnavailableException.class, () -> assistant.generate(1L, "s", "u"));
    }

    @Test
    void overTheRateLimit_isRefusedBeforeCallingTheModel() {
        when(call().content()).thenReturn("ok");
        assistant.generate(1L, "s", "u");
        assistant.generate(1L, "s", "u");
        clearInvocations(chatClient);

        assertThrows(AiRateLimitExceededException.class, () -> assistant.generate(1L, "s", "u"));
        verifyNoInteractions(chatClient);
    }

    @Test
    void status_reportsModelAndLimit() {
        assertTrue(assistant.isEnabled());
        assertEquals(new AiStatusResponse(true, "test-model", 2), assistant.status());
    }

    @Test
    void disabledAssistant_alwaysUnavailable() {
        DisabledAiAssistant disabled = new DisabledAiAssistant(10);

        assertFalse(disabled.isEnabled());
        assertEquals(new AiStatusResponse(false, null, 10), disabled.status());
        assertThrows(AiUnavailableException.class, () -> disabled.generate(1L, "s", "u"));
        assertThrows(AiUnavailableException.class, () -> disabled.generate(1L, "s", "u", Suggestion.class));
    }
}
