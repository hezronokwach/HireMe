package com.example.hireme.equipment;

import com.example.hireme.equipment.dto.CreateEquipmentRequest;
import com.example.hireme.equipment.dto.EquipmentResponse;
import com.example.hireme.equipment.dto.UpdateEquipmentRequest;
import com.example.hireme.equipment.internal.EquipmentEntity;

import java.util.List;

public interface EquipmentService {
    EquipmentResponse create(CreateEquipmentRequest createEquipmentRequest, Long currentUserId);
    EquipmentResponse getById(Long equipmentId);
    List<EquipmentResponse> getAll(EquipmentEntity.Category categoryFilter);
    EquipmentResponse update(Long equipmentId, UpdateEquipmentRequest updateEquipmentRequest, Long currentUserId);
    void delete(Long equipmentId, Long currentUserId);

    boolean isAvailable(Long equipmentId);
    void markAsHired(Long equipmentId);
    void markAsAvailable(Long equipmentId);
}
