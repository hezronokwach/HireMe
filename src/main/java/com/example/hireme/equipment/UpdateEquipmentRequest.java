package com.example.hireme.equipment;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateEquipmentRequest(
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,
        Category category,
        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,
        @Positive(message = "Daily rate must be positive")
        BigDecimal dailyRateKes,
        @Size(max = 255, message = "Location must be at most 255 characters")
        String location,
        EquipmentStatus status
) {
}
