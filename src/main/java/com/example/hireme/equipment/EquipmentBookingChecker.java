package com.example.hireme.equipment;

import java.time.LocalDate;
import java.util.Set;

/**
 * Lets the equipment module ask about bookings without depending on the booking module
 * (booking already depends on equipment, so the reverse would be a cycle).
 * The booking module provides the implementation.
 */
public interface EquipmentBookingChecker {

    // true when the equipment has PENDING, CONFIRMED or ACTIVE bookings that end today or later
    boolean hasOpenBookings(Long equipmentId);

    // ids of equipment with a CONFIRMED or ACTIVE booking on any day in [from, to], both included
    Set<Long> findBookedEquipmentIds(LocalDate from, LocalDate to);
}
