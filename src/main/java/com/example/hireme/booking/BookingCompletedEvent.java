package com.example.hireme.booking;

import java.time.Instant;

public record BookingCompletedEvent(
        Long bookingId,
        Long equipmentId,
        Instant occurredAt
) {
}
