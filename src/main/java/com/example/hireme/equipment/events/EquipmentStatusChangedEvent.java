package com.example.hireme.equipment.events;

import com.example.hireme.equipment.internal.EquipmentEntity;

import java.time.Instant;

public record EquipmentStatusChangedEvent(
        Long equipmentId,
        EquipmentEntity.EquipmentStatus newStatus,
        Instant occuredAt
) {
}
