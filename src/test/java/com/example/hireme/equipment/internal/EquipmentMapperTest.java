package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentMapperTest {

    private final EquipmentMapper equipmentMapper = new EquipmentMapper();

    @Test
    void requestToEntity_mapsAllFields() {
        CreateEquipmentRequest request = new CreateEquipmentRequest("Excavator", Category.HEAVY_MACHINERY,
                "Big machine", BigDecimal.valueOf(1000), "Nairobi");

        EquipmentEntity entity = equipmentMapper.requestToEntity(request, 1L);

        assertEquals(1L, entity.getOwnerId());
        assertEquals("Excavator", entity.getName());
        assertEquals(Category.HEAVY_MACHINERY, entity.getCategory());
        assertEquals("Big machine", entity.getDescription());
        assertEquals(BigDecimal.valueOf(1000), entity.getDailyRateKes());
        assertEquals("Nairobi", entity.getLocation());
        assertEquals(EquipmentStatus.AVAILABLE, entity.getStatus());
        assertNotNull(entity.getCreatedAt());
    }

    @Test
    void toCreateResponse_mapsAllFields() {
        EquipmentEntity entity = new EquipmentEntity();
        entity.setId(1L);
        entity.setOwnerId(2L);
        entity.setName("Excavator");
        entity.setCategory(Category.HEAVY_MACHINERY);
        entity.setDescription("Big machine");
        entity.setDailyRateKes(BigDecimal.valueOf(1000));
        entity.setLocation("Nairobi");
        entity.setStatus(EquipmentStatus.AVAILABLE);
        Instant now = Instant.now();
        entity.setCreatedAt(now);

        EquipmentResponse response = equipmentMapper.toCreateResponse(entity);

        assertEquals(1L, response.id());
        assertEquals(2L, response.ownerId());
        assertEquals("Excavator", response.name());
        assertEquals(Category.HEAVY_MACHINERY, response.category());
        assertEquals("Big machine", response.description());
        assertEquals(BigDecimal.valueOf(1000), response.dailyRateKes());
        assertEquals("Nairobi", response.location());
        assertEquals(EquipmentStatus.AVAILABLE, response.equipmentStatus());
        assertEquals(now, response.createdAt());
    }

    @Test
    void applyUpdate_updatesFields() {
        EquipmentEntity entity = new EquipmentEntity();
        entity.setName("Old Name");
        entity.setDescription("Old Desc");
        entity.setDailyRateKes(BigDecimal.valueOf(500));
        entity.setLocation("Old Location");

        UpdateEquipmentRequest request = new UpdateEquipmentRequest("New Name", null, "New Desc", BigDecimal.valueOf(800), "New Location");

        equipmentMapper.applyUpdate(entity, request);

        assertEquals("New Name", entity.getName());
        assertEquals("New Desc", entity.getDescription());
        assertEquals(BigDecimal.valueOf(800), entity.getDailyRateKes());
        assertEquals("New Location", entity.getLocation());
        assertNotNull(entity.getUpdatedAt());
    }
}
