package com.example.hireme.shared;

import com.example.hireme.shared.exception.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    @Test
    void constructor_withAllParams_setsFields() {
        ErrorResponse response = new ErrorResponse(404, "Not found", Instant.now(), Map.of("id", "missing"));

        assertEquals(404, response.status());
        assertEquals("Not found", response.message());
        assertNotNull(response.timestamp());
        assertEquals("missing", response.errors().get("id"));
    }

    @Test
    void constructor_withStatusAndMessage_setsDefaultValues() {
        ErrorResponse response = new ErrorResponse(500, "Error");

        assertEquals(500, response.status());
        assertEquals("Error", response.message());
        assertNotNull(response.timestamp());
        assertNull(response.errors());
    }
}
