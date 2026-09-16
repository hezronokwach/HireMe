package com.example.hireme.booking;

import java.time.Instant;

public record BookingCancelledEvent(
        Long bookingId,
        Long equipmentId,
        Instant occurredAt
) {
}
