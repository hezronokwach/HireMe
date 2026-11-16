package com.example.hireme.equipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A listing.
 *
 * @param photoUrls its photos, cover first (paths under {@code /api/v1}; empty when it has none)
 */
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
        Instant updatedAt,
        List<String> photoUrls
) {
    public EquipmentResponse {
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }

    /** A listing without photos (yet). */
    public EquipmentResponse(Long id, Long ownerId, String name, Category category, String description,
                             BigDecimal dailyRateKes, String location, EquipmentStatus equipmentStatus,
                             Instant createdAt, Instant updatedAt) {
        this(id, ownerId, name, category, description, dailyRateKes, location, equipmentStatus, createdAt,
                updatedAt, List.of());
    }

    public EquipmentResponse withPhotos(List<String> urls) {
        return new EquipmentResponse(id, ownerId, name, category, description, dailyRateKes, location,
                equipmentStatus, createdAt, updatedAt, urls);
    }
}
