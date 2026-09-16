package com.example.hireme.equipment;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdateEquipmentRequest(
        String name,
        Category category,
        String description,
        @Positive(message = "Daily rate must be positive")
        BigDecimal dailyRateKes,
        String location
) {
}
