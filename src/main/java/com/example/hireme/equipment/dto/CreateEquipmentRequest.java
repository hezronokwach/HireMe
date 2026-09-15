package com.example.hireme.equipment.dto;

import com.example.hireme.equipment.internal.EquipmentEntity.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateEquipmentRequest(
        @NotBlank(message = "Name cannot be blank")
        String name,
        @NotNull(message = "Category is required")
        Category category,
        String description,
        @NotNull(message = "Daily rate cannot be null") @DecimalMin("1")
        BigDecimal dailyRateKes,
        @NotBlank(message = "Location is required")
        String location

) {
}
