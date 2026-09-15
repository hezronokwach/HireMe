package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.dto.CreateEquipmentRequest;
import com.example.hireme.equipment.dto.EquipmentResponse;
import com.example.hireme.equipment.dto.UpdateEquipmentRequest;

import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class EquipmentMapper {
    public EquipmentEntity requestToEntity(CreateEquipmentRequest createEquipmentRequest, Long ownerId) {
        EquipmentEntity equipmentEntity = new EquipmentEntity();
        equipmentEntity.setOwnerId(ownerId);
        equipmentEntity.setName(createEquipmentRequest.name());
        equipmentEntity.setCategory(createEquipmentRequest.category());
        equipmentEntity.setDescription(createEquipmentRequest.description());
        equipmentEntity.setDailyRateKes(createEquipmentRequest.dailyRateKes());
        equipmentEntity.setLocation(createEquipmentRequest.location());
        equipmentEntity.setStatus(EquipmentEntity.EquipmentStatus.AVAILABLE);
        equipmentEntity.setCreatedAt(Instant.now());
        return equipmentEntity;
    }
    public EquipmentResponse toCreateResponse(EquipmentEntity equipmentEntity) {
        return new EquipmentResponse(
                equipmentEntity.getId(),
                equipmentEntity.getOwnerId(),
                equipmentEntity.getName(),
                equipmentEntity.getCategory(),
                equipmentEntity.getDescription(),
                equipmentEntity.getDailyRateKes(),
                equipmentEntity.getLocation(),
                equipmentEntity.getStatus(),
                equipmentEntity.getCreatedAt(),
                equipmentEntity.getUpdatedAt()
        );

    }
    public void applyUpdate(EquipmentEntity existingEquipment, UpdateEquipmentRequest updateEquipmentRequest) {
        existingEquipment.setName(updateEquipmentRequest.name());
        existingEquipment.setDescription(updateEquipmentRequest.description());
        existingEquipment.setDailyRateKes(updateEquipmentRequest.dailyRateKes());
        existingEquipment.setLocation(updateEquipmentRequest.location());
        existingEquipment.setUpdatedAt(Instant.now());
    }
}
