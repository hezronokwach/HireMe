package com.example.hireme.booking;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull(message = "Equipment ID is required")
        Long equipmentId,

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date must be today or in the future")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        @Future(message = "End date must be in the future")
        LocalDate endDate
) {
}
