package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.Category;
import com.example.hireme.equipment.EquipmentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "app_equipment", name = "equipment")
public class EquipmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private String name;

    @Enumerated(EnumType.STRING)
    private Category category;

    private String description;
    private BigDecimal dailyRateKes;
    private String location;

    @Enumerated(EnumType.STRING)
    private EquipmentStatus status;

    private Instant createdAt;
    private Instant updatedAt;
}
