package com.example.hireme.booking;

import java.time.LocalDate;
import java.util.List;

public record EquipmentAvailabilityResponse(
        Long equipmentId,
        LocalDate from,
        LocalDate to,
        // true when the listing takes bookings and no confirmed or active booking falls in [from, to]
        boolean available,
        // confirmed or active bookings overlapping [from, to], earliest first; both dates are booked
        List<BookedPeriod> bookedPeriods
) {
    public record BookedPeriod(LocalDate startDate, LocalDate endDate) {
    }
}
