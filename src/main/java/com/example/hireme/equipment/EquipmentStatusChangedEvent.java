package com.example.hireme.equipment;

import java.time.Instant;

public record EquipmentStatusChangedEvent(
        Long equipmentId,
        EquipmentStatus newStatus,
        Instant occurredAt
) {
}
