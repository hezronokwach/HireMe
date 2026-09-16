package com.example.hireme.shared.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    int status,
    String message,
    Instant timestamp,
    Map<String, String> errors // For validation errors
) {
    public ErrorResponse(int status, String message) {
        this(status, message, Instant.now(), null);
    }
}
