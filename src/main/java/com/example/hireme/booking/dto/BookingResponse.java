package com.example.hireme.booking.dto;

import com.example.hireme.booking.internal.BookingEntity;

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
        BookingEntity.BookingStatus status,
        Instant creationDate
) {
}
