package com.example.hireme.ai;

// What the frontend needs to decide whether to show AI buttons.
public record AiStatusResponse(
        boolean enabled,
        String model,           // null when disabled
        int requestsPerMinute   // per user
) {
}
