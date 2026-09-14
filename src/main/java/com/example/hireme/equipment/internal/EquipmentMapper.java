package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.dto.CreateEquipmentRequest;

public class EquipmentMapper {
    public EquipmentEntity toEntity(CreateEquipmentRequest createEquipmentRequest, Long ownerId) {
        EquipmentEntity equipmentEntity = new EquipmentEntity();
        equipmentEntity.setId(ownerId);
        equipmentEntity.setName(createEquipmentRequest.name());
        equipmentEntity.setDescription(createEquipmentRequest.description());
        equipmentEntity.setDailyRateKes(createEquipmentRequest.dailyRateKes());
        equipmentEntity.setLocation(createEquipmentRequest.location());
        equipmentEntity.setStatus(EquipmentEntity.EquipmentStatus.AVAILABLE);
        equipmentEntity.set
    }
}
