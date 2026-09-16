package com.example.hireme.equipment.internal;

import com.example.hireme.booking.events.BookingCancelledEvent;
import com.example.hireme.booking.events.BookingCreatedEvent;
import com.example.hireme.equipment.EquipmentService;
import lombok.AllArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class EquipmentEventListener {
    private final EquipmentService  equipmentService;
    @ApplicationModuleListener
    void on(BookingCreatedEvent event){
        equipmentService.markAsHired(event.equipmentId());
    }

    @ApplicationModuleListener
    void on(BookingCancelledEvent event){
        equipmentService.markAsAvailable(event.equipmentId());
    }
}
