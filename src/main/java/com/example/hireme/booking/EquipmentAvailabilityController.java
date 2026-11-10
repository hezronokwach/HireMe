package com.example.hireme.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;

// Lives in the booking module because booked dates come from bookings; equipment can't depend on booking.
@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentAvailabilityController {

    static final int DEFAULT_RANGE_DAYS = 90;

    private final BookingService bookingService;
    private final Clock clock;

    @GetMapping("/{equipmentId}/availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EquipmentAvailabilityResponse> getAvailability(
            @PathVariable Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(bookingService.getAvailability(equipmentId, start, end));
    }
}
