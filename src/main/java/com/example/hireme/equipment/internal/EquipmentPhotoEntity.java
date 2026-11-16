package com.example.hireme.equipment.internal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(schema = "app_equipment", name = "equipment_photo")
public class EquipmentPhotoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long equipmentId;
    private String contentType;

    // Loaded only when the picture itself is served; lists use PhotoRef instead.
    @Basic(fetch = FetchType.LAZY)
    private byte[] imageData;

    private int sizeBytes;
    private int sortOrder;
    private Instant createdAt;
}
