package com.example.hireme.shared.event;

import java.time.Instant;

/**
 * Integration event published when a confirmed booking reaches its start date and becomes ACTIVE.
 * <p>
 * Like the other booking events it lives in the {@code shared.event} open module, so other modules
 * can listen to it without depending on {@code booking}.
 */
public record BookingActivatedEvent(
        Long bookingId,
        Long equipmentId,
        Instant occurredAt
) {
}
