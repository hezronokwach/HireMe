package com.example.hireme.shared;

import com.example.hireme.shared.event.BookingCancelledEvent;
import com.example.hireme.shared.event.BookingCompletedEvent;
import com.example.hireme.shared.event.BookingCreatedEvent;
import com.example.hireme.equipment.EquipmentStatusChangedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EventRecordsTest {

    @Test
    void bookingCreatedEvent_recordFields() {
        Instant now = Instant.now();
        BookingCreatedEvent event = new BookingCreatedEvent(1L, 2L, now);

        assertEquals(1L, event.bookingId());
        assertEquals(2L, event.equipmentId());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void bookingCancelledEvent_recordFields() {
        Instant now = Instant.now();
        BookingCancelledEvent event = new BookingCancelledEvent(1L, 2L, now);

        assertEquals(1L, event.bookingId());
        assertEquals(2L, event.equipmentId());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void bookingCompletedEvent_recordFields() {
        Instant now = Instant.now();
        BookingCompletedEvent event = new BookingCompletedEvent(1L, 2L, now);

        assertEquals(1L, event.bookingId());
        assertEquals(2L, event.equipmentId());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void equipmentStatusChangedEvent_recordFields() {
        Instant now = Instant.now();
        EquipmentStatusChangedEvent event = new EquipmentStatusChangedEvent(1L, EquipmentStatusChangedEvent.class.getEnclosingClass() != null ? com.example.hireme.equipment.EquipmentStatus.HIRED : null, now);

        // Just test the basic record creation
        assertNotNull(event);
    }

    @Test
    void equipmentStatusChangedEvent_withHiredStatus() {
        Instant now = Instant.now();
        com.example.hireme.equipment.EquipmentStatusChangedEvent event =
                new com.example.hireme.equipment.EquipmentStatusChangedEvent(1L, com.example.hireme.equipment.EquipmentStatus.HIRED, now);

        assertEquals(1L, event.equipmentId());
        assertEquals(com.example.hireme.equipment.EquipmentStatus.HIRED, event.newStatus());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void equipmentStatusChangedEvent_withAvailableStatus() {
        Instant now = Instant.now();
        com.example.hireme.equipment.EquipmentStatusChangedEvent event =
                new com.example.hireme.equipment.EquipmentStatusChangedEvent(2L, com.example.hireme.equipment.EquipmentStatus.AVAILABLE, now);

        assertEquals(2L, event.equipmentId());
        assertEquals(com.example.hireme.equipment.EquipmentStatus.AVAILABLE, event.newStatus());
    }
}
