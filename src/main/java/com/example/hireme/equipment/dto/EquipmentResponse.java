package com.example.hireme.equipment.dto;

import com.example.hireme.equipment.internal.EquipmentEntity.EquipmentStatus;
import com.example.hireme.equipment.internal.EquipmentEntity.Category;

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
