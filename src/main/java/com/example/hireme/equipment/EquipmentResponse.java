package com.example.hireme.equipment;

import java.math.BigDecimal;
import java.time.Instant;

public record EquipmentResponse(
        Long id,
        Long ownerId,
        String name,
        Category category,
        String description,
        BigDecimal dailyRateKes,
        String location,
        EquipmentStatus equipmentStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
