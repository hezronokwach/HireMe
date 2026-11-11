package com.example.hireme.equipment;

import com.example.hireme.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EquipmentService {
    EquipmentResponse create(CreateEquipmentRequest createEquipmentRequest, Long currentUserId);
    EquipmentResponse getById(Long equipmentId);
    PageResponse<EquipmentResponse> search(EquipmentSearchCriteria criteria, Pageable pageable);
    // The owner's own listings, whatever their status.
    PageResponse<EquipmentResponse> getByOwner(Long ownerId, Pageable pageable);
    EquipmentResponse update(Long equipmentId, UpdateEquipmentRequest updateEquipmentRequest, Long currentUserId);
    void delete(Long equipmentId, Long currentUserId);

    List<Long> getIdsByOwner(Long ownerId);

    // true when the listing can take bookings (status AVAILABLE); booked dates are checked by the booking module
    boolean isAvailable(Long equipmentId);

    // Locks the equipment row until the caller's transaction ends, so changes to its bookings
    // (e.g. confirmations) run one at a time. Must be called inside a transaction.
    void lockForBooking(Long equipmentId);
}
