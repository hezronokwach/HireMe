package com.example.hireme.equipment;

import java.util.List;

public interface EquipmentService {
    EquipmentResponse create(CreateEquipmentRequest createEquipmentRequest, Long currentUserId);
    EquipmentResponse getById(Long equipmentId);
    List<EquipmentResponse> getAll(Category categoryFilter);
    EquipmentResponse update(Long equipmentId, UpdateEquipmentRequest updateEquipmentRequest, Long currentUserId);
    void delete(Long equipmentId, Long currentUserId);

    boolean isAvailable(Long equipmentId);
    void markAsHired(Long equipmentId);
    void markAsAvailable(Long equipmentId);
}
