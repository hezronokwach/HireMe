package com.example.hireme.booking.events;

import java.time.Instant;

public record BookingCancelledEvent(
        Long bookingId,
        Long equipmentId,
        Instant occuredAt
) {
}
