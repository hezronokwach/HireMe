package com.example.hireme.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull
        Long equipmentId,
        @NotNull @FutureOrPresent
        LocalDate startDate,
        @NotNull @Future
        LocalDate endDate

) {
}
