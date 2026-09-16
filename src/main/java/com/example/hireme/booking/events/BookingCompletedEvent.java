package com.example.hireme.booking.events;

import java.time.Instant;

public record BookingCompletedEvent(
        Long bookingId,
        Long equipmentId,
        Instant occuredAt
) {
}
