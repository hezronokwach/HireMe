package com.example.hireme.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A search in the client's own words, e.g. "a tractor in Nakuru next weekend under 8000 a day".
public record AiSearchRequest(
        @NotBlank(message = "Query is required")
        @Size(max = 200, message = "Query must be at most 200 characters")
        String query
) {
}
