package com.example.hireme.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiStatusControllerTest {

    @Test
    void status_returnsTheAssistantsStatus() {
        AiAssistant assistant = mock(AiAssistant.class);
        AiStatusResponse status = new AiStatusResponse(true, "openai/gpt-oss-120b", 10);
        when(assistant.status()).thenReturn(status);

        ResponseEntity<AiStatusResponse> response = new AiStatusController(assistant).status();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(status, response.getBody());
    }
}
