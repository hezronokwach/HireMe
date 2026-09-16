package com.example.hireme.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long bookingId,
        Long equipmentId,
        Long clientId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalCostKes,
        BookingStatus status,
        Instant creationDate
) {
}
