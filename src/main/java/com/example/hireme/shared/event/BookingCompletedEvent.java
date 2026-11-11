package com.example.hireme.shared.event;

import java.time.Instant;

/**
 * Integration event published when a booking is completed.
 * <p>
 * This event is located in the {@code shared.event} open module rather than {@code booking}
 * to prevent circular module dependencies (cycles) between {@code booking} and {@code equipment}
 * under Spring Modulith architectural verification (since {@code booking} queries {@code equipment},
 * and {@code equipment} listens to booking lifecycle events).
 */
public record BookingCompletedEvent(
        Long bookingId,
        Long equipmentId,
        Instant occurredAt
) {
}
