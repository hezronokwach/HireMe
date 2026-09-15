package com.example.hireme.equipment.internal;

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
@Table(schema ="app_equipment", name = "equipment")
public class EquipmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long ownerId;
    String name;
    Category category;
    String description;
    BigDecimal dailyRateKes;
    String location;
    EquipmentStatus status;
    Instant createdAt;
    Instant updatedAt;

    public enum Category{
        HEAVY_MACHINERY,
        AGRICULTURAL,
        EVENT_SETUP
    }

    public enum EquipmentStatus{
        AVAILABLE,
        HIRED,
        UNDER_MAINTENANCE
    }

}



