package com.example.hireme.equipment;

/**
 * Lets the equipment module ask about bookings without depending on the booking module
 * (booking already depends on equipment, so the reverse would be a cycle).
 * The booking module provides the implementation.
 */
public interface EquipmentBookingChecker {

    // true when the equipment has PENDING, CONFIRMED or ACTIVE bookings that end today or later
    boolean hasOpenBookings(Long equipmentId);
}
