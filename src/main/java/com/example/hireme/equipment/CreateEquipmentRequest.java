package com.example.hireme.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateEquipmentRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Category is required")
        Category category,

        String description,

        @NotNull(message = "Daily rate is required")
        @Positive(message = "Daily rate must be greater than zero")
        BigDecimal dailyRateKes,

        @NotBlank(message = "Location is required")
        String location
) {
}
