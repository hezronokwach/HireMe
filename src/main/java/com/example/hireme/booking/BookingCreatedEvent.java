package com.example.hireme.booking;

import java.time.Instant;

public record BookingCreatedEvent(
        Long bookingId,
        Long equipmentId,
        Instant occurredAt
) {
}
